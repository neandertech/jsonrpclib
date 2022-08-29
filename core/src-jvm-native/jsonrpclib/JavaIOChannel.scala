package jsonrpclib

import scala.concurrent.Future
import scala.concurrent.ExecutionContext

import java.io.*
import scala.concurrent.Promise
import scala.annotation.tailrec

class JavaIOChannel(in: DataInputStream, out: DataOutputStream, endpoints: List[Endpoint[Future]])(implicit
    ec: ExecutionContext
) extends FutureBasedChannel(endpoints)
    with PlatformCompat {

  override protected def background[A](maybeCallId: Option[CallId], fa: Future[A]): Future[Unit] = {
    fa.map(_ => ())
  }

  override def sendPayload(payload: Payload): Future[Unit] = {
    Future {
      import internals.*
      val head = LSPHeaders(payload.array.length, Constants.JSON_MIME_TYPE, "utf-8")
      LSPHeaders.write(head, out)
      out.write(payload.array)
    }
  }

  final def start(): Future[Int] = {
    val finish = Promise[Unit]()
    try {
      var fut = finish.future

      @tailrec
      def loop(): Unit = {
        internals.LSPHeaders.readNext(in) match {
          case Right(r) =>
            val msg = new String(Array.fill(r.contentLength)(in.readByte))
            executionContextLoop()
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
