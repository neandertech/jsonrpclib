package jsonrpclib

import io.circe.syntax._
import io.circe.Codec

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

}

object OutputMessage {
  def errorFrom(callId: CallId, protocolError: ProtocolError): OutputMessage =
    ErrorMessage(callId, ErrorPayload(protocolError.code, protocolError.getMessage(), None))

  case class ErrorMessage(callId: CallId, payload: ErrorPayload) extends OutputMessage
  case class ResponseMessage(callId: CallId, data: Payload) extends OutputMessage

}

object Message {
  import jsonrpclib.internals.RawMessage

  implicit val codec: Codec[Message] = Codec.from(
    { c =>
      c.as[RawMessage].flatMap(_.toMessage.left.map(e => io.circe.DecodingFailure(e.getMessage, c.history)))
    },
    RawMessage.from(_).asJson
  )
}
