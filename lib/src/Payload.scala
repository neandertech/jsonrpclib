package jsonrpclib

import java.util.Base64
import com.github.plokhotnyuk.jsoniter_scala.core.JsonValueCodec
import com.github.plokhotnyuk.jsoniter_scala.core.JsonReader
import com.github.plokhotnyuk.jsoniter_scala.core.JsonWriter

sealed trait Payload
object Payload {
  final case class StringPayload(str: String) extends Payload
  final case class BytesPayload(array: Array[Byte]) extends Payload {
    override def equals(other: Any) = other match {
      case bytes: BytesPayload => java.util.Arrays.equals(array, bytes.array)
      case _                   => false
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

  implicit val payloadJsonValueCodec: JsonValueCodec[Payload] = new JsonValueCodec[Payload] {
    def decodeValue(in: JsonReader, default: Payload): Payload = {
      Payload.BytesPayload(in.readRawValAsBytes())
    }
    def encodeValue(x: Payload, out: JsonWriter): Unit = x match {
      case StringPayload(str)  => out.writeRawVal(str.getBytes())
      case BytesPayload(array) => out.writeRawVal(array)
    }
    def nullValue: StringPayload = null
  }
}
