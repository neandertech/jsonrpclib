package jsonrpclib

trait ErrorCodec[E] {

  def encodeBytes(a: E): ErrorPayload
  def encodeString(a: E): ErrorPayload
  def decode(error: ErrorPayload): Either[ProtocolError, E]

}
