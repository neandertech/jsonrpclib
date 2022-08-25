package jsonrpclib

import scala.concurrent.Future
import scala.concurrent.ExecutionContext

import java.io.*

class StreamChannel(in: DataInputStream, out: DataOutputStream, endpoints: List[Endpoint[Future]])(implicit
    ec: ExecutionContext
) extends FutureBasedChannel(endpoints) {

  override protected def background[A](maybeCallId: Option[CallId], fa: Future[A]): Future[Unit] = ???

  override def sendPayload(payload: Payload): Future[Unit] = {
    Future {
      import internals.*
      val head = LSPHeaders(payload.array.length, Constants.JSON_MIME_TYPE, "utf-8")
      LSPHeaders.write(head, out)
      out.write(payload.array)
    }
  }

  final def loop(): Int = {

    internals.LSPHeaders.readNext(in) match {
      case Right(r) =>
        val msg = new String(Array.fill(r.contentLength)(in.readByte))
        handleReceivedPayload(Payload(msg.getBytes))
      case Left(l) => throw l
    }

    scalanative.runtime.loop()
    loop()
  }
}
