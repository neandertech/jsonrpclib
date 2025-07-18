package examples.smithy.server

import cats.effect._
import fs2.io._
import fs2.Stream
import jsonrpclib.fs2._
import jsonrpclib.smithy4sinterop.ClientStub
import jsonrpclib.smithy4sinterop.ServerEndpoints
import jsonrpclib.CallId
import test._ // smithy4s-generated package

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
    val run =
      FS2Channel
        .stream[IO](cancelTemplate = Some(cancelEndpoint))
        .flatMap { channel =>
          Stream.eval(IO.fromEither(ClientStub(TestClient, channel))).flatMap { testClient =>
            Stream.eval(IO.fromEither(ServerEndpoints(new ServerImpl(testClient)))).flatMap { se =>
              channel.withEndpointsStream(se)
            }
          }
        }
        .flatMap { channel =>
          fs2.Stream
            .eval(IO.never) // running the server forever
            .concurrently(stdin[IO](512).through(lsp.decodeMessages).through(channel.inputOrBounce))
            .concurrently(channel.output.through(lsp.encodeMessages).through(stdout[IO]))
        }

    // Using errorln as stdout is used by the RPC channel
    printErr("Starting server") >> run.compile.drain.guarantee(printErr("Terminating server"))
  }

}
