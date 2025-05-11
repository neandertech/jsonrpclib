package jsonrpclib.smithy4sinterop

import jsonrpclib.Codec
import jsonrpclib.Payload
import jsonrpclib.ProtocolError
import smithy4s.Document
import com.github.plokhotnyuk.jsoniter_scala.core._
import smithy4s.Schema
import smithy4s.Document._
import io.circe._

private[jsonrpclib] object CirceJson {

  implicit def fromSchema[A](implicit schema: Schema[A]): Codec[A] = new Codec[A] {
    def encode(a: A): Payload = {
      Payload(documentToJson(Document.encode(a)))
    }

    def decode(payload: Option[Payload]): Either[ProtocolError, A] = {
      try {
        payload match {
          case Some(Payload(data)) =>
            Document.decode[A](fromJson(data)).left.map(e => ProtocolError.ParseError(e.getMessage))
          case None => Left(ProtocolError.ParseError("Expected to decode a payload"))
        }
      } catch { case e: JsonReaderException => Left(ProtocolError.ParseError(e.getMessage())) }
    }
  }

  private val documentToJson: Document => Json = {
    case DNull            => Json.Null
    case DString(value)   => Json.fromString(value)
    case DBoolean(value)  => Json.fromBoolean(value)
    case DNumber(value)   => Json.fromBigDecimal(value)
    case DArray(values)   => Json.fromValues(values.map(documentToJson))
    case DObject(entries) => Json.fromFields(entries.view.mapValues(documentToJson))
  }

  private def fromJson(json: Json): Document = json.fold(
    jsonNull = DNull,
    jsonBoolean = DBoolean(_),
    jsonNumber = n => DNumber(n.toBigDecimal.get),
    jsonString = DString(_),
    jsonArray = arr => DArray(arr.map(fromJson)),
    jsonObject = obj => DObject(obj.toMap.view.mapValues(fromJson).toMap)
  )
}
