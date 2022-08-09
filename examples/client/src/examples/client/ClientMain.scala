package examples.server

import jsonrpclib.CallId
import jsonrpclib.fs2._
import cats.effect._
import fs2.io._
import eu.monniot.process.Process
import com.github.plokhotnyuk.jsoniter_scala.core.JsonValueCodec
import com.github.plokhotnyuk.jsoniter_scala.macros.JsonCodecMaker
import jsonrpclib.Endpoint
import cats.syntax.all._
import fs2.Stream
import jsonrpclib.StubTemplate

object ClientMain extends IOApp.Simple {

  // Reserving a method for cancelation.
  val cancelTemplate = CancelTemplate.make[CallId]("$/cancel", identity, identity)

  // Creating a datatype that'll serve as a request (and response) of an endpoint
  case class IntWrapper(value: Int)
  object IntWrapper {
    implicit val jcodec: JsonValueCodec[IntWrapper] = JsonCodecMaker.make
  }

  type IOStream[A] = fs2.Stream[IO, A]
  def log(str: String): IOStream[Unit] = Stream.eval(IO.consoleForIO.errorln(str))

  def run: IO[Unit] = {
    // Using errorln as stdout is used by the RPC channel
    val run = for {
      _ <- log("Starting client")
      serverJar <- sys.env.get("SERVER_JAR").liftTo[IOStream](new Exception("SERVER_JAR env var does not exist"))
      serverProcess <- Stream.resource(Process.spawn[IO]("java", "-jar", serverJar))
      fs2Channel <- FS2Channel.lspCompliant[IO](serverProcess.stdout, serverProcess.stdin)
      // Opening the stream to be able to send and receive data
      _ <- fs2Channel.openStream
      // Creating a `IntWrapper => IO[IntWrapper]` stub that can call the server
      increment = fs2Channel.simpleStub[IntWrapper, IntWrapper]("increment")
      result <- Stream.eval(IO.pure(IntWrapper(0)).flatMap(increment))
      _ <- log(s"Client received $result")
      _ <- log("Terminating client")
    } yield ()
    run.compile.drain
  }

}
