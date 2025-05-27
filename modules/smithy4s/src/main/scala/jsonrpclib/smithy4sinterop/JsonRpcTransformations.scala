package jsonrpclib.smithy4sinterop

import smithy4s.~>
import smithy4s.schema.ErrorSchema
import smithy4s.schema.OperationSchema
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

  private val payloadTransformation: Schema ~> Schema = Schema
    .transformTransitivelyK(JsonPayloadTransformation)

  private val errorTransformation: ErrorSchema ~> ErrorSchema =
    new smithy4s.kinds.PolyFunction[ErrorSchema, ErrorSchema] {
      def apply[A](e: ErrorSchema[A]): ErrorSchema[A] = {
        payloadTransformation(e.schema).error(e.unliftError)(e.liftError.unlift)
      }
    }
}
