package examples.smithy.client

import cats.effect._
import cats.syntax.all._
import fs2.Stream
import jsonrpclib.CallId
import jsonrpclib.fs2._
import jsonrpclib.smithy4sinterop.ClientStub
import jsonrpclib.smithy4sinterop.ServerEndpoints
import test._

object SmithyClientMain extends IOApp.Simple {

  // Reserving a method for cancelation.
  val cancelEndpoint = CancelTemplate.make[CallId]("$/cancel", identity, identity)

  type IOStream[A] = fs2.Stream[IO, A]
  def log(str: String): IOStream[Unit] = Stream.eval(IO.consoleForIO.errorln(str))

  // Implementing the generated interface
  object Client extends TestClient[IO] {
    def pong(pong: String): IO[Unit] = IO.consoleForIO.errorln(s"Client received pong: $pong")
  }

  def run: IO[Unit] = {
    val run = for {
      ////////////////////////////////////////////////////////
      /////// BOOTSTRAPPING
      ////////////////////////////////////////////////////////
      _ <- log("Starting client")
      serverJar <- sys.env.get("SERVER_JAR").liftTo[IOStream](new Exception("SERVER_JAR env var does not exist"))
      // Starting the server
      rp <- ChildProcess.spawn[IO]("java", "-jar", serverJar)
      // Creating a channel that will be used to communicate to the server
      fs2Channel <- FS2Channel.stream[IO](cancelTemplate = cancelEndpoint.some)
      // Mounting our implementation of the generated interface onto the channel
      _ <- fs2Channel.withEndpointsStream(ServerEndpoints(Client))
      // Creating stubs to talk to the remote server
      server: TestServer[IO] = ClientStub(test.TestServer, fs2Channel)
      _ <- Stream(())
        .concurrently(fs2Channel.output.through(lsp.encodeMessages).through(rp.stdin))
        .concurrently(rp.stdout.through(lsp.decodeMessages).through(fs2Channel.inputOrBounce))
        .concurrently(rp.stderr.through(fs2.io.stderr[IO]))

      ////////////////////////////////////////////////////////
      /////// INTERACTION
      ////////////////////////////////////////////////////////
      result1 <- Stream.eval(server.greet("Client"))
      _ <- log(s"Client received $result1")
      _ <- Stream.eval(server.ping("Ping"))
    } yield ()
    run.compile.drain.guarantee(IO.consoleForIO.errorln("Terminating client"))
  }

}
