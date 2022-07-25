package jsonrpclib.internals

import com.github.plokhotnyuk.jsoniter_scala.core._
import scala.util.Try

sealed trait CallId
object CallId {
  final case class NumberId(long: Long) extends CallId
  final case class StringId(string: String) extends CallId
  case object NullId extends CallId

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
