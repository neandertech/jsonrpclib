package jsonrpclib.smithy4sinterop

import smithy4s.Document
import smithy4s.Schema
import smithy4s.codecs.PayloadPath

import smithy4s.Document.{Decoder => _, _}
import io.circe._

object CirceJsonCodec {

  def fromSchema[A](implicit schema: Schema[A]): Codec[A] = Codec.from(
    c => {
      c.as[Json]
        .map(fromJson)
        .flatMap { d =>
          Document
            .decode[A](d)
            .left
            .map(e =>
              DecodingFailure(DecodingFailure.Reason.CustomReason(e.getMessage), c.history ++ toCursorOps(e.path))
            )
        }
    },
    a => documentToJson(Document.encode(a))
  )

  private def toCursorOps(path: PayloadPath): List[CursorOp] =
    path.segments.map {
      case PayloadPath.Segment.Label(name) => CursorOp.DownField(name)
      case PayloadPath.Segment.Index(i)    => CursorOp.DownN(i)
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
