package jsonrpclib.smithy4sinterop

import cats.effect.IO
import cats.syntax.all._
import fs2.Stream
import io.circe.Decoder
import io.circe.Encoder
import jsonrpclib._
import jsonrpclib.fs2._
import test._
import test.TestServerOperation.GreetError
import weaver._

import scala.concurrent.duration._

import _root_.fs2.concurrent.SignallingRef

object TestClientSpec extends SimpleIOSuite {
  def testRes(name: TestName)(run: Stream[IO, Expectations]): Unit =
    test(name)(run.compile.lastOrError.timeout(10.second))

  type ClientSideChannel = FS2Channel[IO]
  def setup(endpoints: Endpoint[IO]*) = setupAux(endpoints, None)
  def setup(cancelTemplate: CancelTemplate, endpoints: Endpoint[IO]*) = setupAux(endpoints, Some(cancelTemplate))
  def setupAux(endpoints: Seq[Endpoint[IO]], cancelTemplate: Option[CancelTemplate]): Stream[IO, ClientSideChannel] = {
    for {
      serverSideChannel <- FS2Channel.stream[IO](cancelTemplate = cancelTemplate)
      clientSideChannel <- FS2Channel.stream[IO](cancelTemplate = cancelTemplate)
      _ <- serverSideChannel.withEndpointsStream(endpoints)
      _ <- Stream(())
        .concurrently(clientSideChannel.output.through(serverSideChannel.input))
        .concurrently(serverSideChannel.output.through(clientSideChannel.input))
    } yield {
      clientSideChannel
    }
  }

  testRes("Round trip") {
    implicit val greetInputDecoder: Decoder[GreetInput] = CirceJsonCodec.fromSchema
    implicit val greetOutputEncoder: Encoder[GreetOutput] = CirceJsonCodec.fromSchema
    val endpoint: Endpoint[IO] =
      Endpoint[IO]("greet").simple[GreetInput, GreetOutput](in => IO(GreetOutput(s"Hello ${in.name}")))

    for {
      clientSideChannel <- setup(endpoint)
      clientStub = ClientStub(TestServer, clientSideChannel)
      result <- clientStub.greet("Bob").toStream
    } yield {
      expect.same(result.message, "Hello Bob")
    }
  }

  testRes("Sending notification") {
    implicit val pingInputDecoder: Decoder[PingInput] = CirceJsonCodec.fromSchema

    for {
      ref <- SignallingRef[IO, Option[PingInput]](none).toStream
      endpoint: Endpoint[IO] = Endpoint[IO]("ping").notification[PingInput](p => ref.set(p.some))
      clientSideChannel <- setup(endpoint)
      clientStub = ClientStub(TestServer, clientSideChannel)
      _ <- clientStub.ping("hello").toStream
      result <- ref.discrete.dropWhile(_.isEmpty).take(1)
    } yield {
      expect.same(result, Some(PingInput("hello")))
    }
  }

  testRes("Round trip with jsonPayload") {
    implicit val greetInputDecoder: Decoder[GreetInput] = CirceJsonCodec.fromSchema
    implicit val greetOutputEncoder: Encoder[GreetOutput] = CirceJsonCodec.fromSchema
    val endpoint: Endpoint[IO] =
      Endpoint[IO]("greetWithPayload").simple[GreetInput, GreetOutput](in => IO(GreetOutput(s"Hello ${in.name}")))

    for {
      clientSideChannel <- setup(endpoint)
      clientStub = ClientStub(TestServerWithPayload, clientSideChannel)
      result <- clientStub.greetWithPayload(GreetInputPayload("Bob")).toStream
    } yield {
      expect.same(result.payload.message, "Hello Bob")
    }
  }

  testRes("server returns known error") {
    implicit val greetInputDecoder: Decoder[GreetInput] = CirceJsonCodec.fromSchema
    implicit val greetOutputEncoder: Encoder[GreetOutput] = CirceJsonCodec.fromSchema
    implicit val greetErrorEncoder: Encoder[GreetError] = CirceJsonCodec.fromSchema
    implicit val errEncoder: ErrorEncoder[GreetError] =
      err => ErrorPayload(-1, "error", Some(Payload(greetErrorEncoder(err))))

    val endpoint: Endpoint[IO] =
      Endpoint[IO]("greet").apply[GreetInput, GreetError, GreetOutput](in =>
        IO.pure(Left(GreetError.notWelcomeError(NotWelcomeError(s"${in.name} is not welcome"))))
      )

    for {
      clientSideChannel <- setup(endpoint)
      clientStub = ClientStub(TestServer, clientSideChannel)
      result <- clientStub.greet("Bob").attempt.toStream
    } yield {
      matches(result) { case Left(t: NotWelcomeError) =>
        expect.same(t.msg, s"Bob is not welcome")
      }
    }
  }

  testRes("server returns unknown error") {
    implicit val greetInputDecoder: Decoder[GreetInput] = CirceJsonCodec.fromSchema
    implicit val greetOutputEncoder: Encoder[GreetOutput] = CirceJsonCodec.fromSchema

    val endpoint: Endpoint[IO] =
      Endpoint[IO]("greet").simple[GreetInput, GreetOutput](_ => IO.raiseError(new RuntimeException("boom!")))

    for {
      clientSideChannel <- setup(endpoint)
      clientStub = ClientStub(TestServer, clientSideChannel)
      result <- clientStub.greet("Bob").attempt.toStream
    } yield {
      matches(result) { case Left(t: ErrorPayload) =>
        expect.same(t.code, 0) &&
        expect.same(t.message, "boom!") &&
        expect.same(t.data, None)
      }
    }
  }
}
