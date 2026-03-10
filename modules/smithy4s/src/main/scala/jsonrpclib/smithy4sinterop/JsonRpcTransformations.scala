package jsonrpclib.smithy4sinterop

import jsonrpclib.JsonRpcPayload
import smithy4s.~>
import smithy4s.schema.ErrorSchema
import smithy4s.schema.OperationSchema
import smithy4s.schema.SchemaPartition
import smithy4s.Endpoint
import smithy4s.Schema
import smithy4s.Service

private[jsonrpclib] object JsonRpcTransformations {

  def apply[Alg[_[_, _, _, _, _]]]: Service[Alg] => Service[Alg] =
    _.toBuilder
      .mapEndpointEach(
        Endpoint.mapSchema(
          OperationSchema
            .mapInputK(JsonPayloadTransformation)
            .andThen(OperationSchema.mapOutputK(JsonPayloadTransformation))
            .andThen(OperationSchema.mapErrorK(errorTransformation))
        )
      )
      .build

  private val payloadTransformation: Schema ~> Schema =
    new (Schema ~> Schema) {
      def apply[A](schema: Schema[A]): Schema[A] = {
        schema.findPayload(_.hints.has[JsonRpcPayload]) match {
          case SchemaPartition.TotalMatch(payloadSchema) => payloadSchema
          case _                                         => schema
        }
      }
    }

  private val errorTransformation: ErrorSchema ~> ErrorSchema =
    new smithy4s.kinds.PolyFunction[ErrorSchema, ErrorSchema] {
      def apply[A](e: ErrorSchema[A]): ErrorSchema[A] = {
        payloadTransformation(e.schema).error(e.unliftError)(e.liftError.unlift)
      }
    }
}
