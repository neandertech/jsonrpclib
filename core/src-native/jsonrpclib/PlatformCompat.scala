package jsonrpclib

import scala.concurrent._
import scala.annotation.tailrec
import java.io.EOFException

private[jsonrpclib] trait PlatformCompat { self: JavaIOChannel =>
  final def start()(implicit ec: ExecutionContext): Future[Int] = {
    @tailrec
    def loop(): Unit =
      internals.LSPHeaders.readNext(in) match {
        case Right(r) =>
          val msg = new String(Array.fill(r.contentLength)(in.readByte))
          handleReceivedPayload(Payload(msg.getBytes))
          scalanative.runtime.loop()
          loop()
        case Left(_) =>
          // TODO: figure out why this is
          // required for stable tests
          scalanative.runtime.loop()
      }
    try {
      scalanative.runtime.loop()
      Future { loop(); 0 }
    } catch {
      case _: EOFException => Future.successful(0)
    }
  }
}

private[jsonrpclib] object PlatformCompat {
  def executionContextLoop(): Unit = {
    scalanative.runtime.loop()
  }

  def exclusiveAccess[A](f: => A) = f
}
