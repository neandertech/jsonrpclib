package jsonrpclib

trait ErrorEncoder[E] {
  def encode(a: E): ErrorPayload
}

trait ErrorDecoder[E] {
  def decode(error: ErrorPayload): Either[ProtocolError, E]
}

trait ErrorCodec[E] extends ErrorDecoder[E] with ErrorEncoder[E]

object ErrorCodec {
  implicit val errorPayloadCodec: ErrorCodec[ErrorPayload] = new ErrorCodec[ErrorPayload] {
    def encode(a: ErrorPayload): ErrorPayload = a
    def decode(error: ErrorPayload): Either[ProtocolError, ErrorPayload] = Right(error)
  }
}
