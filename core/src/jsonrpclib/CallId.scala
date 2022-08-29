package jsonrpclib

import com.github.plokhotnyuk.jsoniter_scala.core._

sealed trait CallId
object CallId {
  final case class NumberId(long: Long) extends CallId
  final case class StringId(string: String) extends CallId
  case object NullId extends CallId

  implicit val callIdRW: JsonValueCodec[CallId] = new JsonValueCodec[CallId] {
    def decodeValue(in: JsonReader, default: CallId): CallId = {
      try {
        NumberId(in.readLong())
      } catch {
        case _: JsonReaderException =>
          in.rollbackToken()
          try {
            StringId(in.readString(null))
          } catch {
            case _: JsonReaderException =>
              in.readNullOrError(default, "expected null")
          }
      }
    }

    def encodeValue(x: CallId, out: JsonWriter): Unit = x match {
      case NumberId(long)   => out.writeVal(long)
      case StringId(string) => out.writeVal(string)
      case NullId           => out.writeNull()
    }

    def nullValue: CallId = CallId.NullId
  }
}
