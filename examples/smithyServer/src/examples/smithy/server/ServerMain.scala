package examples.smithy.server

import jsonrpclib.CallId
import jsonrpclib.fs2._
import cats.effect._
import fs2.io._
import jsonrpclib.Endpoint
import cats.syntax.all._
import test._ // smithy4s-generated package
import jsonrpclib.smithy4sinterop.ClientStub
import jsonrpclib.smithy4sinterop.ServerEndpoints

object ServerMain extends IOApp.Simple {

  // Reserving a method for cancelation.
  val cancelEndpoint = CancelTemplate.make[CallId]("$/cancel", identity, identity)

  // Implementing the generated interface
  class ServerImpl(client: TestClient[IO]) extends TestServer[IO] {
    def greet(name: String): IO[GreetOutput] = IO.pure(GreetOutput(s"Server says: hello $name !"))

    def ping(ping: String): IO[Unit] = client.pong(s"Returned to sender: $ping")
  }

  def printErr(s: String): IO[Unit] = IO.consoleForIO.errorln(s)

  def run: IO[Unit] = {
    val run = for {
      channel <- FS2Channel[IO](cancelTemplate = Some(cancelEndpoint))
      testClient <- ClientStub.stream(TestClient, channel)
      _ <- channel.withEndpointsStream(ServerEndpoints(new ServerImpl(testClient)))
      _ <- fs2.Stream
        .eval(IO.never) // running the server forever
        .concurrently(stdin[IO](512).through(lsp.decodeMessages).through(channel.inputOrBounce))
        .concurrently(channel.output.through(lsp.encodeMessages).through(stdout[IO]))
    } yield {}

    // Using errorln as stdout is used by the RPC channel
    printErr("Starting server") >> run.compile.drain.guarantee(printErr("Terminating server"))
  }

}
