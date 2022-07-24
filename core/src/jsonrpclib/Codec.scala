package jsonrpclib

import com.github.plokhotnyuk.jsoniter_scala.core._
import jsonrpclib.Payload.BytesPayload
import jsonrpclib.Payload.StringPayload

trait Codec[A] {

  def encodeBytes(a: A): Payload.BytesPayload
  def encodeString(a: A): Payload.StringPayload
  def decode(payload: Option[Payload]): Either[ProtocolError, A]

}

object Codec {

  def encodeBytes[A](a: A)(implicit codec: Codec[A]): Payload.BytesPayload = codec.encodeBytes(a)
  def encodeString[A](a: A)(implicit codec: Codec[A]): Payload.StringPayload = codec.encodeString(a)
  def decode[A](payload: Option[Payload])(implicit codec: Codec[A]): Either[ProtocolError, A] = codec.decode(payload)

  implicit def fromJsonCodec[A](implicit jsonCodec: JsonValueCodec[A]): Codec[A] = new Codec[A] {
    def encodeBytes(a: A): Payload.BytesPayload = Payload.BytesPayload(writeToArray(a))

    def encodeString(a: A): Payload.StringPayload = Payload.StringPayload(writeToString(a))

    def decode(payload: Option[Payload]): Either[ProtocolError, A] = {
      try {
        payload match {
          case Some(BytesPayload(array)) => Right(readFromArray(array))
          case Some(StringPayload(str))  => Right(readFromString(str))
          case None                      => Left(ProtocolError.ParseError("Expected to decode a payload"))
        }
      } catch { case e: JsonReaderException => Left(ProtocolError.ParseError(e.getMessage())) }
    }
  }

}
