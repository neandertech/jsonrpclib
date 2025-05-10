package jsonrpclib

import io.circe.{Decoder, Encoder, Json}

case class Payload(data: Json) {
  def stripNull: Option[Payload] = Option(Payload(data)).filter(p => !p.data.isNull)
}

object Payload {

  val NullPayload: Payload = Payload(Json.Null)

  private[jsonrpclib] implicit val payloadEncoder: Encoder[Payload] = Encoder[Json].contramap(_.data)
  private[jsonrpclib] implicit val payloadDecoder: Decoder[Payload] = Decoder[Json].map(Payload(_))
}
