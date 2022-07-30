package jsonrpclib

import com.github.plokhotnyuk.jsoniter_scala.core.JsonReader
import com.github.plokhotnyuk.jsoniter_scala.core.JsonValueCodec
import com.github.plokhotnyuk.jsoniter_scala.core.JsonWriter

import java.util.Base64

final case class Payload(array: Array[Byte]) {
  override def equals(other: Any) = other match {
    case bytes: Payload => java.util.Arrays.equals(array, bytes.array)
    case _              => false
  }

  override def hashCode(): Int = {
    var hashCode = 0
    var i = 0
    while (i < array.length) {
      hashCode += array(i).hashCode()
      i += 1
    }
    hashCode
  }

  override def toString = Base64.getEncoder().encodeToString(array)
}
object Payload {

  implicit val payloadJsonValueCodec: JsonValueCodec[Payload] = new JsonValueCodec[Payload] {
    def decodeValue(in: JsonReader, default: Payload): Payload = {
      Payload(in.readRawValAsBytes())
    }

    def encodeValue(bytes: Payload, out: JsonWriter): Unit =
      out.writeRawVal(bytes.array)

    def nullValue: Payload = null
  }
}
