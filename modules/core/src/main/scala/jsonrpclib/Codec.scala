package jsonrpclib

import io.circe.{Encoder, Decoder}

trait Codec[A] {

  def encode(a: A): Payload
  def decode(payload: Option[Payload]): Either[ProtocolError, A]
}

object Codec {

  def encode[A](a: A)(implicit codec: Codec[A]): Payload = codec.encode(a)
  def decode[A](payload: Option[Payload])(implicit codec: Codec[A]): Either[ProtocolError, A] = codec.decode(payload)

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
