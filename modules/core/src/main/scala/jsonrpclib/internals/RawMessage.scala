package jsonrpclib
package internals

import io.circe.{Decoder, Encoder, Json}
import io.circe.syntax._

private[jsonrpclib] case class RawMessage(
    jsonrpc: String,
    method: Option[String] = None,
    result: Option[Option[Payload]] = None,
    error: Option[ErrorPayload] = None,
    params: Option[Payload] = None,
    id: Option[CallId] = None
) {

  def toMessage: Either[ProtocolError, Message] = (id, method) match {
    case (Some(callId), Some(method)) =>
      Right(InputMessage.RequestMessage(method, callId, params))
    case (None, Some(method)) =>
      Right(InputMessage.NotificationMessage(method, params))
    case (Some(callId), None) =>
      (error, result) match {
        case (Some(error), _) => Right(OutputMessage.ErrorMessage(callId, error))
        case (_, Some(data))  => Right(OutputMessage.ResponseMessage(callId, data.getOrElse(Payload.NullPayload)))
        case (None, None) =>
          Left(
            ProtocolError.InvalidRequest(
              "call id was set and method unset, but neither result, nor error fields were present"
            )
          )
      }
    case (None, None) =>
      Left(
        ProtocolError.InvalidRequest(
          "neither call id nor method were set"
        )
      )
  }
}

private[jsonrpclib] object RawMessage {

  val `2.0` = "2.0"

  def from(message: Message): RawMessage = message match {
    case InputMessage.NotificationMessage(method, params) =>
      RawMessage(`2.0`, method = Some(method), params = params)
    case InputMessage.RequestMessage(method, callId, params) =>
      RawMessage(`2.0`, method = Some(method), params = params, id = Some(callId))
    case OutputMessage.ErrorMessage(callId, errorPayload) =>
      RawMessage(`2.0`, error = Some(errorPayload), id = Some(callId))
    case OutputMessage.ResponseMessage(callId, data) =>
      RawMessage(`2.0`, result = Some(data.stripNull), id = Some(callId))
  }

  // Custom encoder to flatten nested Option[Option[Payload]]
  implicit val rawMessageEncoder: Encoder[RawMessage] = Encoder.instance { msg =>
    Json
      .obj(
        "jsonrpc" -> Json.fromString(msg.jsonrpc),
        "method" -> msg.method.asJson,
        "params" -> msg.params.asJson,
        "error" -> msg.error.asJson,
        "id" -> msg.id.asJson
      )
      .deepMerge(
        msg.result match {
          case Some(Some(payload)) => Json.obj("result" -> payload.asJson)
          case Some(None)          => Json.obj("result" -> Json.Null)
          case None                => Json.obj()
        }
      )
  }

  // Custom decoder to wrap result into Option[Option[Payload]]
  implicit val rawMessageDecoder: Decoder[RawMessage] = Decoder.instance { c =>
    for {
      jsonrpc <- c.downField("jsonrpc").as[String]
      method <- c.downField("method").as[Option[String]]
      params <- c.downField("params").as[Option[Payload]]
      error <- c.downField("error").as[Option[ErrorPayload]]
      id <- c.downField("id").as[Option[CallId]]
      resultOpt <-
        if (c.downField("result").succeeded)
          c.downField("result").as[Option[Payload]].map(res => Some(res))
        else Right(None)
    } yield RawMessage(jsonrpc, method, resultOpt, error, params, id)
  }
}
