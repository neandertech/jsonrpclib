package jsonrpclib.smithy4sinterop

import jsonrpclib.JsonRpcPayload
import smithy4s.~>
import smithy4s.Schema
import smithy4s.Schema.StructSchema

private[jsonrpclib] object JsonPayloadTransformation extends (Schema ~> Schema) {

  def apply[A0](fa: Schema[A0]): Schema[A0] =
    fa match {
      case struct: StructSchema[b] =>
        struct.fields
          .collectFirst {
            case field if field.hints.has[JsonRpcPayload] =>
              field.schema.biject[b]((f: Any) => struct.make(Vector(f)))(field.get)
          }
          .getOrElse(fa)
      case _ => fa
    }
}
