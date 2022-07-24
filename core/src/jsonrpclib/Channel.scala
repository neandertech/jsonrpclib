package jsonrpclib

import jsonrpclib.EndpointTemplate.NotificationTemplate
import jsonrpclib.EndpointTemplate.RequestResponseTemplate

trait Channel[F[_]] {
  def mountEndpoint(endpoint: Endpoint[F]): F[Unit]
  def unmountEndpoint(method: String): F[Unit]

  def notificationStub[In](method: String)(implicit inCodec: Codec[In]): In => F[Unit]
  def requestResponseStub[In, Err, Out](
      method: String
  )(implicit inCodec: Codec[In], errCodec: ErrorCodec[Err], outCodec: Codec[Out]): In => F[Either[Err, Out]]

  def clientStub[In, Err, Out](template: EndpointTemplate[In, Err, Out]): In => F[Either[Err, Out]]
}

object Channel {

  protected[jsonrpclib] abstract class MonadicChannel[F[_]](implicit F: Monadic[F]) extends Channel[F] {

    final def clientStub[In, Err, Out](template: EndpointTemplate[In, Err, Out]): In => F[Either[Err, Out]] =
      template match {
        case NotificationTemplate(method, inCodec) =>
          val stub = notificationStub(method)(inCodec)
          (in: In) => F.doFlatMap(stub(in))(unit => F.doPure(Right(unit)))
        case RequestResponseTemplate(method, inCodec, errCodec, outCodec) =>
          requestResponseStub(method)(inCodec, errCodec, outCodec)
      }
  }

}
