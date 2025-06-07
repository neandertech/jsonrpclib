package jsonrpclib.smithy4sinterop

import io.circe.{Decoder => CirceDecoder, _}
import smithy4s.codecs.PayloadPath
import smithy4s.schema.CachedSchemaCompiler
import smithy4s.Document
import smithy4s.Document.{Encoder => _, _}
import smithy4s.Schema

class CirceDecoderImpl extends CachedSchemaCompiler[CirceDecoder] {
  val decoder: CachedDecoderCompilerImpl = new CachedDecoderCompilerImpl()

  type Cache = decoder.Cache
  def createCache(): Cache = decoder.createCache()

  def fromSchema[A](schema: Schema[A], cache: Cache): CirceDecoder[A] =
    c => {
      c.as[Json]
        .map(fromJson(_))
        .flatMap { d =>
          decoder
            .fromSchema(schema, cache)
            .decode(d)
            .left
            .map(e =>
              DecodingFailure(DecodingFailure.Reason.CustomReason(e.getMessage), c.history ++ toCursorOps(e.path))
            )
        }
    }

  def fromSchema[A](schema: Schema[A]): CirceDecoder[A] = fromSchema(schema, createCache())

  private def toCursorOps(path: PayloadPath): List[CursorOp] =
    path.segments.map {
      case PayloadPath.Segment.Label(name) => CursorOp.DownField(name)
      case PayloadPath.Segment.Index(i)    => CursorOp.DownN(i)
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
