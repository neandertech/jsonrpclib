package jsonrpclib

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import jsonrpclib.internals._
import jsonrpclib.Payload.BytesPayload
import jsonrpclib.Payload.StringPayload
import FutureBasedChannel._
import scala.concurrent.Promise
import java.util.concurrent.atomic.AtomicLong
import jsonrpclib.Endpoint.NotificationEndpoint
import jsonrpclib.Endpoint.RequestResponseEndpoint
import scala.util.Try

trait Channel[F[_]] {
  protected def handleReceivedPayload(msg: Payload): F[Unit]
  protected def sendPayload(msg: Payload): F[Unit]
  def reportError(params: Option[Payload], error: ProtocolError, method: String): F[Unit]
}

class FutureBasedChannel(endpoints: List[Endpoint[Future]])(implicit ec: ExecutionContext)
    extends Channel[Future]
    with MessageDispatcher[Future] {

  override def createPromise[A](): Future[(Try[A] => Future[Unit], () => Future[A])] = Future.successful {
    val promise = Promise[A]()
    val fulfill: Try[A] => Future[Unit] = (a: Try[A]) => Future.successful(promise.complete(a))
    val future: () => Future[A] = () => promise.future
    (fulfill, future)
  }

  protected def storePendingCall(callId: CallId, handle: OutputMessage => Future[Unit]): Future[Unit] =
    Future.successful(pending.put(callId, handle))
  protected def getPendingCall(callId: CallId): Future[Option[OutputMessage => Future[Unit]]] =
    Future.successful(Option(pending.get(callId)))
  protected def getEndpoint(method: String): Future[Option[Endpoint[Future]]] =
    Future.successful(endpointsMap.get(method))
  protected def doFlatMap[A, B](fa: Future[A])(f: A => Future[B]): Future[B] = fa.flatMap(f)
  protected def doPure[A](a: A): Future[A] = Future.successful(a)
  protected def sendMessage(message: Message): Future[Unit] = {
    sendPayload(Codec.encodeBytes(message))
    Future.successful(())
  }
  protected def nextCallId(): Future[CallId] = Future.successful(CallId.NumberId(nextID.incrementAndGet()))

  private[this] val endpointsMap: Map[String, Endpoint[Future]] = endpoints.map(ep => ep.method -> ep).toMap
  private[this] val pending = new java.util.concurrent.ConcurrentHashMap[CallId, OutputMessage => Future[Unit]]
  private[this] val nextID = new AtomicLong(0L)
  @volatile
  private[this] var closeReason: Throwable = _

  def sendPayload(msg: Payload): Future[Unit] = ???
  def reportError(params: Option[Payload], error: ProtocolError, method: String): Future[Unit] = ???

}

object FutureBasedChannel {
  private trait PendingCall {}

  private object PendingCall {
    def apply[R](p: Promise[R])(implicit s: Codec[R]): PendingCall = {
      new PendingCall {
        type Resp = R
        val promise: Promise[Resp] = p
        implicit val codec: Codec[R] = s
      }
    }
  }
}
