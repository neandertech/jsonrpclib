package jsonrpclib.smithy4sinterop

import smithy4s.~>
import cats.MonadThrow
import jsonrpclib.fs2._
import smithy4s.Service
import smithy4s.schema._
import cats.effect.kernel.Async
import smithy4s.kinds.PolyFunction5
import smithy4s.ShapeId
import cats.syntax.all._
import smithy4s.json.Json
import jsonrpclib.Codec._
import com.github.plokhotnyuk.jsoniter_scala.core._

object ClientStub {

  def apply[Alg[_[_, _, _, _, _]], F[_]](service: Service[Alg], channel: FS2Channel[F])(implicit
      F: Async[F]
  ): F[service.Impl[F]] = new ClientStub(service, channel).compile

  def stream[Alg[_[_, _, _, _, _]], F[_]](service: Service[Alg], channel: FS2Channel[F])(implicit
      F: Async[F]
  ): fs2.Stream[F, service.Impl[F]] = fs2.Stream.eval(new ClientStub(service, channel).compile)
}

private class ClientStub[Alg[_[_, _, _, _, _]], F[_]](val service: Service[Alg], channel: FS2Channel[F])(implicit
    F: Async[F]
) {

  def compile: F[service.Impl[F]] = precompileAll.map { stubCache =>
    val interpreter = new service.FunctorInterpreter[F] {
      def apply[I, E, O, SI, SO](op: service.Operation[I, E, O, SI, SO]): F[O] = {
        val smithy4sEndpoint = service.endpoint(op)
        val input = service.input(op)
        (stubCache(smithy4sEndpoint): F[I => F[O]]).flatMap { stub =>
          stub(input)
        }
      }
    }
    service.fromPolyFunction(interpreter)
  }

  private type Stub[I, E, O, SI, SO] = F[I => F[O]]
  private val precompileAll: F[PolyFunction5[service.Endpoint, Stub]] = {
    F.ref(Map.empty[ShapeId, Any]).flatMap { cache =>
      service.endpoints.toList
        .traverse_ { ep =>
          val shapeId = ep.id
          EndpointSpec.fromHints(ep.hints).liftTo[F](NotJsonRPCEndpoint(shapeId)).flatMap { epSpec =>
            val stub = jsonRPCStub(ep, epSpec)
            cache.update(_ + (shapeId -> stub))
          }
        }
        .as {
          new PolyFunction5[service.Endpoint, Stub] {
            def apply[I, E, O, SI, SO](ep: service.Endpoint[I, E, O, SI, SO]): Stub[I, E, O, SI, SO] = {
              cache.get.map { c =>
                c(ep.id).asInstanceOf[I => F[O]]
              }
            }
          }
        }
    }
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
        channel.notificationStub[I](methodName).andThen(f => f *> coerce)
      case EndpointSpec.Request(methodName) =>
        channel.simpleStub[I, O](methodName)
    }
  }

  case class NotJsonRPCEndpoint(shapeId: ShapeId) extends Throwable
  case object NotUnitReturnType extends Throwable

  private object CoerceUnitVisitor extends (Schema ~> F) {
    def apply[A](schema: Schema[A]): F[A] = schema match {
      case s @ Schema.StructSchema(_, _, _, make) if s.isUnit =>
        MonadThrow[F].unit.asInstanceOf[F[A]]
      case _ => MonadThrow[F].raiseError[A](NotUnitReturnType)
    }
  }

  private def coerceUnit[A](schema: Schema[A]): F[A] = CoerceUnitVisitor(schema)

}
