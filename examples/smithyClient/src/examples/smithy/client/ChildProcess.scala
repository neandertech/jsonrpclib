package examples.smithy.client

import fs2.Stream
import cats.effect._
import cats.syntax.all._
import scala.jdk.CollectionConverters._
import java.io.OutputStream

trait ChildProcess[F[_]] {
  def stdin: fs2.Pipe[F, Byte, Unit]
  def stdout: Stream[F, Byte]
  def stderr: Stream[F, Byte]
}

object ChildProcess {

  def spawn[F[_]: Async](command: String*): Stream[F, ChildProcess[F]] =
    Stream.resource(startRes(command))

  val readBufferSize = 512

  private def startRes[F[_]: Async](command: Seq[String]) = Resource
    .make {
      Async[F].interruptible(new java.lang.ProcessBuilder(command.asJava).start())
    } { p =>
      Sync[F].interruptible(p.destroy())
    }
    .map { p =>
      val done = Async[F].fromCompletableFuture(Sync[F].delay(p.onExit()))
      new ChildProcess[F] {
        def stdin: fs2.Pipe[F, Byte, Unit] =
          writeOutputStreamFlushingChunks[F](Sync[F].interruptible(p.getOutputStream()))

        def stdout: fs2.Stream[F, Byte] = fs2.io
          .readInputStream[F](Sync[F].interruptible(p.getInputStream()), chunkSize = readBufferSize)

        def stderr: fs2.Stream[F, Byte] = fs2.io
          .readInputStream[F](Sync[F].blocking(p.getErrorStream()), chunkSize = readBufferSize)
          // Avoids broken pipe - we cut off when the program ends.
          // Users can decide what to do with the error logs using the exitCode value
          .interruptWhen(done.void.attempt)
      }
    }

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
