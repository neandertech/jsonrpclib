package examples.smithy.client

import cats.effect._
import cats.syntax.all._
import fs2.Stream
import fs2.io._
import jsonrpclib.CallId
import jsonrpclib.fs2._
import jsonrpclib.smithy4sinterop.ClientStub
import jsonrpclib.smithy4sinterop.ServerEndpoints
import test._

import java.io.InputStream
import java.io.OutputStream

object SmithyClientMain extends IOApp.Simple {

  // Reserving a method for cancelation.
  val cancelEndpoint = CancelTemplate.make[CallId]("$/cancel", identity, identity)

  type IOStream[A] = fs2.Stream[IO, A]
  def log(str: String): IOStream[Unit] = Stream.eval(IO.consoleForIO.errorln(str))

  // Implementing the generated interface
  object Client extends TestClient[IO] {
    def greet(name: String): IO[GreetOutput] = IO.pure(GreetOutput(s"Client says: hello $name !"))
    def pong(pong: String): IO[Unit] = IO.consoleForIO.errorln(s"Client received pong: $pong")
  }

  def run: IO[Unit] = {
    import scala.concurrent.duration._
    val run = for {
      ////////////////////////////////////////////////////////
      /////// BOOTSTRAPPING
      ////////////////////////////////////////////////////////
      _ <- log("Starting client")
      serverJar <- sys.env.get("SERVER_JAR").liftTo[IOStream](new Exception("SERVER_JAR env var does not exist"))
      // Starting the server
      rp <- ChildProcess.spawn[IO]("java", "-jar", serverJar)
      // Creating a channel that will be used to communicate to the server
      fs2Channel <- FS2Channel[IO](cancelTemplate = cancelEndpoint.some)
      // Mounting our implementation of the generated interface onto the channel
      _ <- fs2Channel.withEndpointsStream(ServerEndpoints(Client))
      // Creating stubs to talk to the remote server
      server: TestServer[IO] <- ClientStub.stream(test.TestServer, fs2Channel)
      _ <- Stream(())
        .concurrently(fs2Channel.output.through(lsp.encodePayloads).through(rp.stdin))
        .concurrently(rp.stdout.through(lsp.decodePayloads).through(fs2Channel.input))
        .concurrently(rp.stderr.through(fs2.io.stderr[IO]))

      ////////////////////////////////////////////////////////
      /////// INTERACTION
      ////////////////////////////////////////////////////////
      result1 <- Stream.eval(server.greet("Client"))
      _ <- log(s"Client received $result1")
      _ <- Stream.eval(server.ping("Ping"))
    } yield ()
    run.compile.drain
  }

}
