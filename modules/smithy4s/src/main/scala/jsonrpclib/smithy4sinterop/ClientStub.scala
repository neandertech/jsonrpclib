package jsonrpclib.smithy4sinterop

import smithy4s.~>
import smithy4s.Service
import smithy4s.schema._
import smithy4s.ShapeId
import smithy4s.json.Json
import jsonrpclib.Codec._
import com.github.plokhotnyuk.jsoniter_scala.core._
import jsonrpclib.Channel
import jsonrpclib.Monadic

object ClientStub {

  def apply[Alg[_[_, _, _, _, _]], F[_]: Monadic](service: Service[Alg], channel: Channel[F]): service.Impl[F] =
    new ClientStub(service, channel).compile
}

private class ClientStub[Alg[_[_, _, _, _, _]], F[_]: Monadic](val service: Service[Alg], channel: Channel[F]) {

  def compile: service.Impl[F] = {
    val interpreter = new service.FunctorEndpointCompiler[F] {
      def apply[I, E, O, SI, SO](e: service.Endpoint[I, E, O, SI, SO]): I => F[O] = {
        val shapeId = e.id
        val spec = EndpointSpec.fromHints(e.hints).toRight(NotJsonRPCEndpoint(shapeId)).toTry.get

        jsonRPCStub(e, spec)
      }
    }

    service.impl(interpreter)
  }

  private val jsoniterCodecGlobalCache = Json.jsoniter.createCache()

  private def deriveJsonCodec[A](schema: Schema[A]): JsonCodec[A] =
    Json.jsoniter.fromSchema(schema, jsoniterCodecGlobalCache)

  def jsonRPCStub[I, E, O, SI, SO](
      smithy4sEndpoint: service.Endpoint[I, E, O, SI, SO],
      endpointSpec: EndpointSpec
  ): I => F[O] = {

    implicit val inputCodec: JsonCodec[I] = deriveJsonCodec(smithy4sEndpoint.input)
    implicit val outputCodec: JsonCodec[O] = deriveJsonCodec(smithy4sEndpoint.output)

    endpointSpec match {
      case EndpointSpec.Notification(methodName) =>
        val coerce = coerceUnit[O](smithy4sEndpoint.output)
        channel.notificationStub[I](methodName).andThen(f => Monadic[F].doFlatMap(f)(_ => coerce))
      case EndpointSpec.Request(methodName) =>
        channel.simpleStub[I, O](methodName)
    }
  }

  case class NotJsonRPCEndpoint(shapeId: ShapeId) extends Throwable
  case object NotUnitReturnType extends Throwable

  private object CoerceUnitVisitor extends (Schema ~> F) {
    def apply[A](schema: Schema[A]): F[A] = schema match {
      case s @ Schema.StructSchema(_, _, _, make) if s.isUnit =>
        Monadic[F].doPure(()).asInstanceOf[F[A]]
      case _ => Monadic[F].doRaiseError[A](NotUnitReturnType)
    }
  }

  private def coerceUnit[A](schema: Schema[A]): F[A] = CoerceUnitVisitor(schema)

}
