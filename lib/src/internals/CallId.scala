package jsonrpclib.internals

import io.circe.Json
import com.github.plokhotnyuk.jsoniter_scala.core._
import scala.util.Try
import io.circe.Codec
import io.circe.{Decoder, HCursor}
import io.circe.JsonNumber
import io.circe.JsonObject
import io.circe.DecodingFailure

private[jsonrpclib] sealed trait CallId
private[jsonrpclib] object CallId {
  final case class NumberId(long: Long) extends CallId
  final case class StringId(string: String) extends CallId
  case object NullId extends CallId

  implicit val callIdCodec: Codec[CallId] = new Codec[CallId] {
    def apply(c: HCursor): Decoder.Result[CallId] = c.value.foldWith(
      new Json.Folder[Decoder.Result[CallId]] {
        val error = Left(DecodingFailure("Expected string, integer or null", c.history))
        def onNull: Decoder.Result[CallId] = Right(NullId)
        def onString(value: String): Decoder.Result[CallId] = Right(StringId(value))
        def onNumber(value: JsonNumber): Decoder.Result[CallId] = value.toLong match {
          case None        => error
          case Some(value) => Right(NumberId(value))
        }
        def onBoolean(value: Boolean): Decoder.Result[CallId] = error
        def onArray(value: Vector[Json]): Decoder.Result[CallId] = error
        def onObject(value: JsonObject): Decoder.Result[CallId] = error
      }
    )
    def apply(a: CallId): Json = a match {
      case NumberId(long)   => Json.fromLong(long)
      case StringId(string) => Json.fromString(string)
      case NullId           => Json.Null
    }
  }

  implicit val callIdRW: JsonValueCodec[CallId] = new JsonValueCodec[CallId] {
    def decodeValue(in: JsonReader, default: CallId): CallId =
      Try(in.readLong())
        .map(NumberId(_): CallId)
        .orElse(Try(in.readString(null)).map(StringId(_): CallId))
        .orElse(scala.util.Success(default))
        .get

    def encodeValue(x: CallId, out: JsonWriter): Unit = x match {
      case NumberId(long)   => out.writeVal(long)
      case StringId(string) => out.writeVal(string)
      case NullId           => out.writeNull()
    }

    def nullValue: CallId = CallId.NullId
  }
}
