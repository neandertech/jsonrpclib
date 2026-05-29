package jsonrpclib

import io.circe.syntax._
import io.circe.Codec
import io.circe.DecodingFailure

sealed trait Message { def maybeCallId: Option[CallId] }
sealed trait InputMessage extends Message { def method: String }
sealed trait OutputMessage extends Message {
  def callId: CallId
  final override def maybeCallId: Option[CallId] = Some(callId)
}

object InputMessage {
  case class RequestMessage(method: String, callId: CallId, params: Payload) extends InputMessage {
    def maybeCallId: Option[CallId] = Some(callId)
  }

  case class NotificationMessage(method: String, params: Payload) extends InputMessage {
    def maybeCallId: Option[CallId] = None
  }

  implicit val codec: Codec[InputMessage] = Codec.from(
    Message.codec.emap {
      case input: InputMessage => Right(input)
      case _: OutputMessage    => Left("expected a JSON-RPC request or notification, got a response")
    },
    Message.codec.contramap[InputMessage](identity)
  )
}

object OutputMessage {
  def errorFrom(callId: CallId, protocolError: ProtocolError): OutputMessage =
    ErrorMessage(callId, ErrorPayload(protocolError.code, protocolError.getMessage(), None))

  case class ErrorMessage(callId: CallId, payload: ErrorPayload) extends OutputMessage
  case class ResponseMessage(callId: CallId, data: Payload) extends OutputMessage

  implicit val codec: Codec[OutputMessage] = Codec.from(
    Message.codec.emap {
      case output: OutputMessage => Right(output)
      case _: InputMessage       => Left("expected a JSON-RPC response, got a request or notification")
    },
    Message.codec.contramap[OutputMessage](identity)
  )
}

object Message {
  import jsonrpclib.internals.RawMessage

  implicit val codec: Codec[Message] = Codec.from(
    { c =>
      c.as[RawMessage].flatMap(_.toMessage.left.map(e => DecodingFailure(e.getMessage, c.history)))
    },
    RawMessage.from(_).asJson
  )
}
