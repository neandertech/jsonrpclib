package jsonrpclib.smithy4sinterop

import io.circe._
import smithy4s.schema.CachedSchemaCompiler
import smithy4s.Schema

object CirceJsonCodec {

  object Encoder extends CirceEncoderImpl
  object Decoder extends CirceDecoderImpl

  object Codec extends CachedSchemaCompiler[Codec] {
    type Cache = (Encoder.Cache, Decoder.Cache)
    def createCache(): Cache = (Encoder.createCache(), Decoder.createCache())

    def fromSchema[A](schema: Schema[A]): Codec[A] =
      io.circe.Codec.from(Decoder.fromSchema(schema), Encoder.fromSchema(schema))

    def fromSchema[A](schema: Schema[A], cache: Cache): Codec[A] =
      io.circe.Codec.from(
        Decoder.fromSchema(schema, cache._2),
        Encoder.fromSchema(schema, cache._1)
      )
  }

  /** Creates a Circe `Codec[A]` from a Smithy4s `Schema[A]`.
    *
    * This enables encoding values of type `A` to JSON and decoding JSON back into `A`, using the structure defined by
    * the Smithy schema.
    */
  def fromSchema[A](implicit schema: Schema[A]): Codec[A] =
    Codec.fromSchema(schema)
}
