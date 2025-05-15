package jsonrpclib.smithy4sinterop

import cats.effect.IO
import fs2.Stream
import jsonrpclib._
import test.TestServer
import weaver._

import scala.concurrent.duration._
import jsonrpclib.fs2._
import test.GreetOutput
import io.circe.Encoder
import test.GreetInput
import io.circe.Decoder

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
      server: TestServer[IO] = ClientStub(TestServer, clientSideChannel)
      result <- server.greet("Bob").toStream
    } yield {
      expect.same(result.message, "Hello Bob")
    }
  }
}
