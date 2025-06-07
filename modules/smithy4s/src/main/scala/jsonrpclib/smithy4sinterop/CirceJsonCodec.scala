package jsonrpclib.smithy4sinterop

import io.circe._
import smithy4s.schema.FieldFilter
import smithy4s.Schema

object CirceJsonCodec {

  object Encoder extends CirceEncoderImpl(FieldFilter.Default)
  object Decoder extends CirceDecoderImpl

  /** Creates a Circe `Codec[A]` from a Smithy4s `Schema[A]`.
    *
    * This enables encoding values of type `A` to JSON and decoding JSON back into `A`, using the structure defined by
    * the Smithy schema.
    */
  def fromSchema[A](implicit schema: Schema[A]): Codec[A] =
    Codec.from(Decoder.fromSchema(schema), Encoder.fromSchema(schema))
}
