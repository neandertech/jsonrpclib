package jsonrpclib

import java.util.Base64
import jsonrpclib.Payload.Data
import jsonrpclib.Payload.NullPayload
import io.circe.{Decoder, Encoder, Json}

sealed trait Payload extends Product with Serializable {
  def stripNull: Option[Payload.Data] = this match {
    case d @ Data(_) => Some(d)
    case NullPayload => None
  }
}

object Payload {
  def apply(value: Array[Byte]): Payload =
    if (value == null) NullPayload else Data(value)

  final case class Data(array: Array[Byte]) extends Payload {
    override def equals(other: Any): Boolean = other match {
      case bytes: Data => java.util.Arrays.equals(array, bytes.array)
      case _           => false
    }

    override lazy val hashCode: Int = java.util.Arrays.hashCode(array)

    override def toString: String = Base64.getEncoder.encodeToString(array)
  }

  case object NullPayload extends Payload

  private[jsonrpclib] implicit val payloadEncoder: Encoder[Payload] = Encoder.instance {
    case Data(arr) =>
      val base64 = Base64.getEncoder.encodeToString(arr)
      Json.fromString(base64)
    case NullPayload =>
      Json.Null
  }

  private[jsonrpclib] implicit val payloadDecoder: Decoder[Payload] = Decoder.instance { c =>
    c.as[String] match {
      case Right(base64str) =>
        try {
          Right(Data(Base64.getDecoder.decode(base64str)))
        } catch {
          case _: IllegalArgumentException =>
            Left(io.circe.DecodingFailure(s"Invalid base64 string: $base64str", c.history))
        }
      case Left(_) =>
        if (c.value.isNull) Right(NullPayload)
        else Left(io.circe.DecodingFailure("Expected base64 string or null", c.history))
    }
  }
}
