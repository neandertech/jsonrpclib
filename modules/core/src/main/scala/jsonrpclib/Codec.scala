package jsonrpclib

import com.github.plokhotnyuk.jsoniter_scala.core._
import com.github.plokhotnyuk.jsoniter_scala.circe.JsoniterScalaCodec._
import io.circe.Json
import io.circe.{Encoder, Decoder}

trait Codec[A] {

  def encode(a: A): Payload
  def decode(payload: Option[Payload]): Either[ProtocolError, A]

}

object Codec {

  def encode[A](a: A)(implicit codec: Codec[A]): Payload = codec.encode(a)
  def decode[A](payload: Option[Payload])(implicit codec: Codec[A]): Either[ProtocolError, A] = codec.decode(payload)

  implicit def fromJsonCodec[A](implicit jsonCodec: JsonValueCodec[A]): Codec[A] = new Codec[A] {
    def encode(a: A): Payload = {
      Payload(writeToArray(a))
    }

    def decode(payload: Option[Payload]): Either[ProtocolError, A] = {
      try {
        payload match {
          case Some(Payload.Data(payload)) => Right(readFromArray[A](payload))
          case Some(Payload.NullPayload)   => Right(readFromArray[A](nullArray))
          case None                        => Left(ProtocolError.ParseError("Expected to decode a payload"))
        }
      } catch { case e: JsonReaderException => Left(ProtocolError.ParseError(e.getMessage())) }
    }
  }

  implicit def fromCirceCodecs[A: Encoder: Decoder]: Codec[A] = new Codec[A] {
    def encode(a: A): Payload = {
      Payload(writeToArray[Json](Encoder[A].apply(a)))
    }

    def decode(payload: Option[Payload]): Either[ProtocolError, A] = {
      def decodeImpl(bytes: Array[Byte]) =
        readFromArray[Json](bytes).as[A].left.map(e => ProtocolError.ParseError(e.getMessage))

      payload match {
        case Some(Payload.Data(payload)) => decodeImpl(payload)
        case Some(Payload.NullPayload)   => decodeImpl(nullArray)
        case None                        => Left(ProtocolError.ParseError("Expected to decode a payload"))
      }
    }
  }

  private val nullArray = "null".getBytes()

}
