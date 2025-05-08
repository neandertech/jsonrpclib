package jsonrpclib

import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto._

case class ErrorPayload(code: Int, message: String, data: Option[Payload]) extends Throwable {
  override def getMessage(): String = s"JsonRPC Error $code: $message"
}

object ErrorPayload {

  implicit val decoder: Decoder[ErrorPayload] = deriveDecoder
  implicit val encoder: Encoder[ErrorPayload] = deriveEncoder
}
