package jsonrpclib
package internals

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import jsonrpclib.internals._
import jsonrpclib.Payload.BytesPayload
import jsonrpclib.Payload.StringPayload
import scala.concurrent.Promise
import java.util.concurrent.atomic.AtomicLong
import jsonrpclib.Endpoint.NotificationEndpoint
import jsonrpclib.Endpoint.RequestResponseEndpoint
import jsonrpclib.EndpointTemplate.NotificationTemplate
import jsonrpclib.EndpointTemplate.RequestResponseTemplate
import jsonrpclib.internals.OutputMessage.ErrorMessage
import jsonrpclib.internals.OutputMessage.ResponseMessage
import scala.util.Try

private[jsonrpclib] trait MessageDispatcher[F[_]] {

  protected def reportError(params: Option[Payload], error: ProtocolError, method: String): F[Unit]

  protected def doFlatMap[A, B](fa: F[A])(f: A => F[B]): F[B]
  protected def doPure[A](a: A): F[A]
  protected def getEndpoint(method: String): F[Option[Endpoint[F]]]
  protected def sendMessage(message: Message): F[Unit]
  protected def nextCallId(): F[CallId]
  protected def createPromise[A](): F[(Try[A] => F[Unit], () => F[A])]
  protected def storePendingCall(callId: CallId, handle: OutputMessage => F[Unit]): F[Unit]
  protected def getPendingCall(callId: CallId): F[Option[OutputMessage => F[Unit]]]

  protected def notificationStub[In](method: String)(implicit inCodec: Codec[In]): In => F[Unit] = { (input: In) =>
    val encoded = inCodec.encodeBytes(input)
    val message = InputMessage.NotificationMessage(method, Some(encoded))
    sendMessage(message)
  }

  protected def requestResponseStub[In, Err, Out](
      method: String
  )(implicit inCodec: Codec[In], errCodec: ErrorCodec[Err], outCodec: Codec[Out]): In => F[Either[Err, Out]] = {
    (input: In) =>
      val encoded = inCodec.encodeBytes(input)
      doFlatMap(nextCallId()) { callId =>
        val message = InputMessage.RequestMessage(method, callId, Some(encoded))
        doFlatMap(createPromise[Either[Err, Out]]()) { case (fulfill, future) =>
          val pc = createPendingCall(method, errCodec, outCodec, fulfill)
          doFlatMap(storePendingCall(callId, pc))(_ => future())
        }
      }
  }

  protected def handleReceivedPayload(payload: Payload): F[Unit] = {
    Codec.decode[Message](Some(payload)).map {
      case im: InputMessage =>
        doFlatMap(getEndpoint(im.method)) {
          case Some(ep) => executeInputMessage(im, ep)
          case None =>
            im.maybeCallId match {
              case None =>
                // notification : do nothing
                doPure(())
              case Some(callId) =>
                val error = ProtocolError.MethodNotFound(im.method)
                sendProtocolError(callId, error)
            }
        }
      case im: OutputMessage => doPure(())
    } match {
      case Left(error) =>
        sendProtocolError(error)
      case Right(dispatch) => dispatch
    }
  }

  private def sendProtocolError(callId: CallId, pError: ProtocolError): F[Unit] =
    sendMessage(OutputMessage.errorFrom(callId, pError))
  private def sendProtocolError(pError: ProtocolError): F[Unit] =
    sendProtocolError(CallId.NullId, pError)

  private def executeInputMessage(input: InputMessage, endpoint: Endpoint[F]): F[Unit] = {
    (input, endpoint) match {
      case (InputMessage.NotificationMessage(_, params), ep: NotificationEndpoint[F, in]) =>
        ep.inCodec.decode(params) match {
          case Right(value) => ep.run(value)
          case Left(value)  => reportError(params, value, ep.method)
        }
      case (InputMessage.RequestMessage(_, callId, params), ep: RequestResponseEndpoint[F, in, err, out]) =>
        ep.inCodec.decode(params) match {
          case Right(value) =>
            doFlatMap(ep.run(value)) {
              case Right(data) =>
                val responseData = ep.outCodec.encodeBytes(data)
                sendMessage(OutputMessage.ResponseMessage(callId, responseData))
              case Left(error) =>
                val errorPayload = ep.errCodec.encodeBytes(error)
                sendMessage(OutputMessage.ErrorMessage(callId, errorPayload))
            }
          case Left(pError) =>
            sendProtocolError(callId, pError)
        }
      case (InputMessage.NotificationMessage(_, params), ep: RequestResponseEndpoint[F, in, err, out]) =>
        val message = s"This ${ep.method} endpoint cannot process notifications, request is missing callId"
        val pError = ProtocolError.InvalidRequest(message)
        sendProtocolError(pError)
      case (InputMessage.RequestMessage(method, callId, params), ep: NotificationEndpoint[F, in]) =>
        val message = s"This ${ep.method} endpoint expects notifications and cannot return a result"
        val pError = ProtocolError.InvalidRequest(message)
        sendProtocolError(pError)
    }
  }

  private def createPendingCall[Err, Out](
      method: String,
      errCodec: ErrorCodec[Err],
      outCodec: Codec[Out],
      fulfill: Try[Either[Err, Out]] => F[Unit]
  ): OutputMessage => F[Unit] = { message: OutputMessage =>
    message match {
      case ErrorMessage(_, errorPayload) =>
        errCodec.decode(errorPayload) match {
          case Left(decodeError) => fulfill(scala.util.Failure(decodeError))
          case Right(value)      => fulfill(scala.util.Success(Left(value)))
        }
      case ResponseMessage(_, data) =>
        outCodec.decode(Some(data)) match {
          case Left(decodeError) => fulfill(scala.util.Failure(decodeError))
          case Right(value)      => fulfill(scala.util.Success(Right(value)))
        }
    }
  }

}
