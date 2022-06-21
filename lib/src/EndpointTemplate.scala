package jsonrpclib

sealed trait EndpointTemplate

object EndpointTemplate {
  final case class NotificationTemplate[In](
      method: String,
      inCodec: Codec[In]
  ) extends EndpointTemplate

  final case class RequestResponseTemplate[In, Err, Out](
      method: String,
      inCodec: Codec[In],
      errCodec: ErrorCodec[Err],
      outCodec: Codec[Out]
  ) extends EndpointTemplate
}
