package jsonrpclib

import jsonrpclib.StubTemplate.NotificationTemplate
import jsonrpclib.StubTemplate.RequestResponseTemplate

trait Channel[F[_]] {
  def mountEndpoint(endpoint: Endpoint[F]): F[Unit]
  def unmountEndpoint(method: String): F[Unit]

  def notificationStub[In: Codec](method: String): In => F[Unit]
  def simpleStub[In: Codec, Out: Codec](method: String): In => F[Out]
  def stub[In: Codec, Err: ErrorCodec, Out: Codec](method: String): In => F[Either[Err, Out]]
  def stub[In, Err, Out](template: StubTemplate[In, Err, Out]): In => F[Either[Err, Out]]
}

object Channel {

  protected[jsonrpclib] abstract class MonadicChannel[F[_]](implicit F: Monadic[F]) extends Channel[F] {

    final def stub[In, Err, Out](template: StubTemplate[In, Err, Out]): In => F[Either[Err, Out]] =
      template match {
        case NotificationTemplate(method, inCodec) =>
          val stub = notificationStub(method)(inCodec)
          (in: In) => F.doFlatMap(stub(in))(unit => F.doPure(Right(unit)))
        case RequestResponseTemplate(method, inCodec, errCodec, outCodec) =>
          stub(method)(inCodec, errCodec, outCodec)
      }

    final def simpleStub[In: Codec, Out: Codec](method: String): In => F[Out] = {
      val s = stub[In, ErrorPayload, Out](method)
      (in: In) =>
        F.doFlatMap(s(in)) {
          case Left(e)  => F.doRaiseError(e)
          case Right(a) => F.doPure(a)
        }
    }
  }

}
