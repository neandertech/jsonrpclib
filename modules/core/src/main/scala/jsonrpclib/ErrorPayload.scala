package jsonrpclib

import io.circe.Decoder
import io.circe.Encoder

case class ErrorPayload(code: Int, message: String, data: Option[Payload]) extends Throwable {
  override def getMessage(): String = s"JsonRPC Error $code: $message"
}

object ErrorPayload {

  implicit val errorPayloadEncoder: Encoder[ErrorPayload] =
    Encoder.forProduct3("code", "message", "data")(e => (e.code, e.message, e.data))

  implicit val errorPayloadDecoder: Decoder[ErrorPayload] =
    Decoder.forProduct3("code", "message", "data")(ErrorPayload.apply)
}
