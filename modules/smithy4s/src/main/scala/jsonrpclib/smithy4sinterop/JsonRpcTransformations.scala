package jsonrpclib.smithy4sinterop

import smithy4s.~>
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
            .mapInputK(payloadTransformation)
            .andThen(OperationSchema.mapOutputK(payloadTransformation))
        )
      )
      .build

  private val payloadTransformation: Schema ~> Schema = Schema
    .transformTransitivelyK(JsonPayloadTransformation)
}
