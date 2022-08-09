package examples.server

import jsonrpclib.CallId
import jsonrpclib.fs2._
import cats.effect._
import fs2.io._
import com.github.plokhotnyuk.jsoniter_scala.core.JsonValueCodec
import com.github.plokhotnyuk.jsoniter_scala.macros.JsonCodecMaker
import jsonrpclib.Endpoint
import cats.syntax.all._
import fs2.Stream
import jsonrpclib.StubTemplate
import cats.effect.std.Dispatcher
import scala.sys.process.ProcessIO
import cats.effect.implicits._
import scala.sys.process.{Process => SProcess}
import java.io.OutputStream
import java.io.InputStream

object ClientMain extends IOApp.Simple {

  // Reserving a method for cancelation.
  val cancelEndpoint = CancelTemplate.make[CallId]("$/cancel", identity, identity)

  // Creating a datatype that'll serve as a request (and response) of an endpoint
  case class IntWrapper(value: Int)
  object IntWrapper {
    implicit val jcodec: JsonValueCodec[IntWrapper] = JsonCodecMaker.make
  }

  type IOStream[A] = fs2.Stream[IO, A]
  def log(str: String): IOStream[Unit] = Stream.eval(IO.consoleForIO.errorln(str))

  def run: IO[Unit] = {
    import scala.concurrent.duration._
    // Using errorln as stdout is used by the RPC channel
    val run = for {
      _ <- log("Starting client")
      serverJar <- sys.env.get("SERVER_JAR").liftTo[IOStream](new Exception("SERVER_JAR env var does not exist"))
      // Starting the server
      (serverStdin, serverStdout, serverStderr) <- Stream.resource(process("java", "-jar", serverJar))
      pipeErrors = serverStderr.through(fs2.io.stderr)
      // Creating a channel that will be used to communicate to the server
      fs2Channel <- FS2Channel
        .lspCompliant[IO](serverStdout, serverStdin, cancelTemplate = cancelEndpoint.some)
        .concurrently(pipeErrors)
      // Opening the stream to be able to send and receive data
      _ <- fs2Channel.openStream
      // Creating a `IntWrapper => IO[IntWrapper]` stub that can call the server
      increment = fs2Channel.simpleStub[IntWrapper, IntWrapper]("increment")
      result <- Stream.eval(increment(IntWrapper(0)))
      _ <- log(s"Client received $result")
      _ <- log("Terminating client")
    } yield ()
    run.compile.drain.timeout(2.second)
  }

  /** Wraps the spawning of a subprocess into fs2 friendly semantics
    */
  import scala.concurrent.duration._
  def process(command: String*) = for {
    dispatcher <- Dispatcher[IO]
    stdinPromise <- IO.deferred[fs2.Pipe[IO, Byte, Unit]].toResource
    stdoutPromise <- IO.deferred[fs2.Stream[IO, Byte]].toResource
    stderrPromise <- IO.deferred[fs2.Stream[IO, Byte]].toResource
    makeProcessBuilder = IO(sys.process.stringSeqToProcess(command))
    makeProcessIO = IO(
      new ProcessIO(
        in = { (outputStream: OutputStream) =>
          val pipe = writeOutputStreamFlushingChunks(IO(outputStream))
          val fulfil = stdinPromise.complete(pipe)
          dispatcher.unsafeRunSync(fulfil)
        },
        out = { (inputStream: InputStream) =>
          val stream = fs2.io.readInputStream(IO(inputStream), 512)
          val fulfil = stdoutPromise.complete(stream)
          dispatcher.unsafeRunSync(fulfil)
        },
        err = { (inputStream: InputStream) =>
          val stream = fs2.io.readInputStream(IO(inputStream), 512)
          val fulfil = stderrPromise.complete(stream)
          dispatcher.unsafeRunSync(fulfil)
        }
      )
    )
    makeProcess = (makeProcessBuilder, makeProcessIO).flatMapN { case (b, io) => IO.blocking(b.run(io)) }
    _ <- Resource.make(makeProcess)((runningProcess) => IO.blocking(runningProcess.destroy()))
    pipes <- (stdinPromise.get, stdoutPromise.get, stderrPromise.get).tupled.toResource
  } yield pipes

  /** Adds a flush after each chunk
    */
  def writeOutputStreamFlushingChunks[F[_]](
      fos: F[OutputStream],
      closeAfterUse: Boolean = true
  )(implicit F: Sync[F]): fs2.Pipe[F, Byte, Nothing] =
    s => {
      def useOs(os: OutputStream): Stream[F, Nothing] =
        s.chunks.foreach(c => F.interruptible(os.write(c.toArray)) >> F.blocking(os.flush()))

      val os =
        if (closeAfterUse) Stream.bracket(fos)(os => F.blocking(os.close()))
        else Stream.eval(fos)
      os.flatMap(os => useOs(os) ++ Stream.exec(F.blocking(os.flush())))
    }
}
