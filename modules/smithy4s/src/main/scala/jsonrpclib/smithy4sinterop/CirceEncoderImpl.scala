package jsonrpclib.smithy4sinterop

import io.circe._
import smithy4s.schema.CachedSchemaCompiler
import smithy4s.schema.FieldFilter
import smithy4s.Document
import smithy4s.Document.{Encoder => _, _}
import smithy4s.Schema

class CirceEncoderImpl(fieldFilter: FieldFilter) extends CachedSchemaCompiler[Encoder] {
  val encoder: CachedEncoderCompilerImpl = new CachedEncoderCompilerImpl(fieldFilter)

  type Cache = encoder.Cache
  def createCache(): Cache = encoder.createCache()

  def fromSchema[A](schema: Schema[A], cache: Cache): Encoder[A] =
    a => documentToJson(encoder.fromSchema(schema, cache).encode(a))

  def fromSchema[A](schema: Schema[A]): Encoder[A] = fromSchema(schema, createCache())

  private val documentToJson: Document => Json = {
    case DNull            => Json.Null
    case DString(value)   => Json.fromString(value)
    case DBoolean(value)  => Json.fromBoolean(value)
    case DNumber(value)   => Json.fromBigDecimal(value)
    case DArray(values)   => Json.fromValues(values.map(documentToJson))
    case DObject(entries) => Json.fromFields(entries.view.mapValues(documentToJson))
  }
}
