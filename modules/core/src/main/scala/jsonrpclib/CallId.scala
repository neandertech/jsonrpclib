package jsonrpclib

import io.circe.{Decoder, Json, Codec}

sealed trait CallId
object CallId {
  final case class NumberId(long: Long) extends CallId
  final case class StringId(string: String) extends CallId
  case object NullId extends CallId

  implicit val codec: Codec[CallId] = Codec.from(
    Decoder
      .decodeOption(Decoder.decodeString.map(StringId(_): CallId).or(Decoder.decodeLong.map(NumberId(_): CallId)))
      .map {
        case None    => NullId
        case Some(v) => v
      },
    {
      case NumberId(n)   => Json.fromLong(n)
      case StringId(str) => Json.fromString(str)
      case NullId        => Json.Null
    }
  )
}
