package jsonrpclib

import io.circe.Json
import io.circe.{Encoder, Decoder}
import com.github.plokhotnyuk.jsoniter_scala.core._
import com.github.plokhotnyuk.jsoniter_scala.circe.JsoniterScalaCodec._

trait Codec[A] {

  def encode(a: A): Payload
  def decode(payload: Option[Payload]): Either[ProtocolError, A]
}

object Codec {

  def encode[A](a: A)(implicit codec: Codec[A]): Payload = codec.encode(a)
  def decode[A](payload: Option[Payload])(implicit codec: Codec[A]): Either[ProtocolError, A] = codec.decode(payload)

  // TODO replace with CirceJson.fromSchema
  implicit def fromJsonCodec[A](implicit jsonCodec: JsonValueCodec[A]): Codec[A] = new Codec[A] {
    def encode(a: A): Payload = {
      Payload(readFromArray[Json](writeToArray(a)))
    }

    def decode(payload: Option[Payload]): Either[ProtocolError, A] = {
      try {
        payload match {
          case Some(Payload(data)) => Right(readFromArray[A](writeToArray(data)))
          case None                => Left(ProtocolError.ParseError("Expected to decode a payload"))
        }
      } catch { case e: JsonReaderException => Left(ProtocolError.ParseError(e.getMessage())) }
    }
  }

  implicit def fromCirceCodecs[A: Encoder: Decoder]: Codec[A] = new Codec[A] {
    def encode(a: A): Payload = {
      Payload(Encoder[A].apply(a))
    }

    def decode(payload: Option[Payload]): Either[ProtocolError, A] = {
      payload match {
        case Some(Payload(payload)) => payload.as[A].left.map(e => ProtocolError.ParseError(e.getMessage))
        case None                   => Left(ProtocolError.ParseError("Expected to decode a payload"))
      }
    }
  }
}
