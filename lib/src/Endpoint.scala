package jsonrpclib

sealed trait Endpoint[F[_]] {
  def method: String
}

object Endpoint {

  final case class NotificationEndpoint[F[_], In](method: String, run: In => F[Unit], inCodec: Codec[In])
      extends Endpoint[F]

  final case class RequestResponseEndpoint[F[_], In, Err, Out](
      method: String,
      run: In => F[Either[Err, Out]],
      inCodec: Codec[In],
      errCodec: ErrorCodec[Err],
      outCodec: Codec[Out]
  ) extends Endpoint[F]

}
