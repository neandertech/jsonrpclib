package jsonrpclib

import io.circe.{Decoder, Encoder, HCursor, Json}

sealed trait CallId
object CallId {
  final case class NumberId(long: Long) extends CallId
  final case class StringId(string: String) extends CallId
  case object NullId extends CallId

  implicit val callIdDecoder: Decoder[CallId] = Decoder.instance { cursor =>
    cursor.value.fold(
      jsonNull = Right(NullId),
      jsonNumber = num => num.toLong.map(NumberId(_)).toRight(decodingError(cursor)),
      jsonString = str => Right(StringId(str)),
      jsonBoolean = _ => Left(decodingError(cursor)),
      jsonArray = _ => Left(decodingError(cursor)),
      jsonObject = _ => Left(decodingError(cursor))
    )
  }

  implicit val callIdEncoder: Encoder[CallId] = Encoder.instance {
    case NumberId(n)   => Json.fromLong(n)
    case StringId(str) => Json.fromString(str)
    case NullId        => Json.Null
  }

  private def decodingError(cursor: HCursor) =
    io.circe.DecodingFailure("CallId must be number, string, or null", cursor.history)
}
