package jsonrpclib.smithy4sinterop

import cats.effect.IO
import fs2.Stream
import jsonrpclib._
import test.TestServer
import weaver._
import cats.syntax.all._

import scala.concurrent.duration._
import jsonrpclib.fs2._
import test.GreetOutput
import io.circe.Encoder
import test.GreetInput
import io.circe.Decoder
import test.PingInput
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
    implicit val greetInputDecoder: Decoder[GreetInput] = CirceJson.fromSchema
    implicit val greetOutputEncoder: Encoder[GreetOutput] = CirceJson.fromSchema
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
    implicit val pingInputDecoder: Decoder[PingInput] = CirceJson.fromSchema

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
}
