package jsonrpclib

import scala.concurrent.Future
import scala.concurrent.ExecutionContext

import java.io.*

class JavaIOChannel(protected val in: DataInputStream, out: DataOutputStream, endpoints: List[Endpoint[Future]])(
    implicit ec: ExecutionContext
) extends FutureBasedChannel(endpoints)
    with PlatformCompat {

  override protected def background[A](maybeCallId: Option[CallId], fa: Future[A]): Future[Unit] = {
    fa.map(_ => ())
  }

  override def sendPayload(payload: Payload): Future[Unit] = {
    Future.successful {
      import internals.*
      val head = LSPHeaders(payload.array.length, Constants.JSON_MIME_TYPE, "utf-8")

      PlatformCompat.exclusiveAccess {
        LSPHeaders.write(head, out)
        out.write(payload.array)
      }
    }
  }

}
