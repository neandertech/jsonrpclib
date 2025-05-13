package jsonrpclib.smithy4sinterop

import smithy4s.Document
import smithy4s.Schema
import smithy4s.Document.{Decoder => _, _}
import io.circe._

private[jsonrpclib] object CirceJson {

  def fromSchema[A](implicit schema: Schema[A]): Codec[A] = new Codec[A] {
    def apply(a: A): Json =
      documentToJson(Document.encode(a))

    def apply(c: HCursor): Decoder.Result[A] =
      c.as[Json]
        .map(fromJson)
        .flatMap(Document.decode[A])
        .left
        .map(e => DecodingFailure.apply(DecodingFailure.Reason.CustomReason(e.getMessage), c.history))
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
