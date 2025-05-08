package jsonrpclib

import io.circe.{Decoder, Encoder}
import io.circe.syntax._
import io.circe.generic.semiauto._

sealed trait Message { def maybeCallId: Option[CallId] }
sealed trait InputMessage extends Message { def method: String }
sealed trait OutputMessage extends Message {
  def callId: CallId
  final override def maybeCallId: Option[CallId] = Some(callId)
}

object InputMessage {
  case class RequestMessage(method: String, callId: CallId, params: Option[Payload]) extends InputMessage {
    def maybeCallId: Option[CallId] = Some(callId)
  }

  case class NotificationMessage(method: String, params: Option[Payload]) extends InputMessage {
    def maybeCallId: Option[CallId] = None
  }

  implicit val requestDecoder: Decoder[RequestMessage] = deriveDecoder
  implicit val requestEncoder: Encoder[RequestMessage] = deriveEncoder

  implicit val notificationDecoder: Decoder[NotificationMessage] = deriveDecoder
  implicit val notificationEncoder: Encoder[NotificationMessage] = deriveEncoder
}

object OutputMessage {
  def errorFrom(callId: CallId, protocolError: ProtocolError): OutputMessage =
    ErrorMessage(callId, ErrorPayload(protocolError.code, protocolError.getMessage(), None))

  case class ErrorMessage(callId: CallId, payload: ErrorPayload) extends OutputMessage
  case class ResponseMessage(callId: CallId, data: Payload) extends OutputMessage

  implicit val errorDecoder: Decoder[ErrorMessage] = deriveDecoder
  implicit val errorEncoder: Encoder[ErrorMessage] = deriveEncoder

  implicit val responseDecoder: Decoder[ResponseMessage] = deriveDecoder
  implicit val responseEncoder: Encoder[ResponseMessage] = deriveEncoder
}

object Message {
  import jsonrpclib.internals.RawMessage

  implicit val decoder: Decoder[Message] = Decoder.instance { c =>
    c.as[RawMessage].flatMap(_.toMessage.left.map(e => io.circe.DecodingFailure(e.getMessage, c.history)))
  }

  implicit val encoder: Encoder[Message] = Encoder.instance { msg =>
    RawMessage.from(msg).asJson
  }
}
