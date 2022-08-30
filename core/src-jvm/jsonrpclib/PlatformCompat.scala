package jsonrpclib

import scala.annotation.tailrec
import scala.concurrent.Future
import scala.concurrent.Promise
import java.io.EOFException
import scala.concurrent.ExecutionContext
import java.util.concurrent.Semaphore

private[jsonrpclib] trait PlatformCompat { self: JavaIOChannel =>
  final def start()(implicit ec: ExecutionContext): Future[Int] = {
    val finish = Promise[Unit]()
    try {
      var fut = finish.future

      @tailrec
      def loop(): Unit = {
        internals.LSPHeaders.readNext(in) match {
          case Right(r) =>
            val msg = new String(Array.fill(r.contentLength)(in.readByte))
            PlatformCompat.executionContextLoop()
            fut = handleReceivedPayload(Payload(msg.getBytes())).zip(fut).map(_._1)
            loop()
          case Left(_) =>
            finish.completeWith(Future.unit)
        }
      }

      loop()

      fut.map(_ => 0)
    } catch {
      case _: EOFException => Future.successful(0)
    }
  }
}

private[jsonrpclib] object PlatformCompat {
  def executionContextLoop(): Unit = ()

  private val semaphore = new Semaphore(1, true)

  def exclusiveAccess[A](f: => A) = {

    try {
      semaphore.acquire()
      f
    } finally {
      semaphore.release()
    }

  }
}
