package jsonrpclib.smithy4sinterop

import smithy4s.codecs.PayloadError
import smithy4s.internals.DocumentDecoderSchemaVisitor
import smithy4s.internals.DocumentEncoderSchemaVisitor
import smithy4s.schema.CachedSchemaCompiler
import smithy4s.schema.FieldFilter
import smithy4s.Document
import smithy4s.Document._
import smithy4s.Schema

private[smithy4sinterop] class CachedEncoderCompilerImpl(fieldFilter: FieldFilter)
    extends CachedSchemaCompiler.DerivingImpl[Encoder]
    with EncoderCompiler {

  protected type Aux[A] = smithy4s.internals.DocumentEncoder[A]

  def fromSchema[A](
      schema: Schema[A],
      cache: Cache
  ): Encoder[A] = {
    val makeEncoder =
      schema.compile(
        new DocumentEncoderSchemaVisitor(cache, fieldFilter)
      )
    new Encoder[A] {
      def encode(a: A): Document = {
        makeEncoder.apply(a)
      }
    }
  }

  def withFieldFilter(
      fieldFilter: FieldFilter
  ): EncoderCompiler = new CachedEncoderCompilerImpl(
    fieldFilter
  )
}

private[smithy4sinterop] class CachedDecoderCompilerImpl extends CachedSchemaCompiler.Impl[Decoder] {

  protected type Aux[A] = smithy4s.internals.DocumentDecoder[A]

  def fromSchema[A](
      schema: Schema[A],
      cache: Cache
  ): Decoder[A] = {
    val decodeFunction =
      schema.compile(new DocumentDecoderSchemaVisitor(cache))
    new Decoder[A] {
      def decode(a: Document): Either[PayloadError, A] =
        try { Right(decodeFunction(Nil, a)) }
        catch {
          case e: PayloadError => Left(e)
        }
    }
  }
}
