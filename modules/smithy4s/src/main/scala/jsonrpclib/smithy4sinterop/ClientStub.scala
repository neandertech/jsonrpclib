package jsonrpclib.smithy4sinterop

import cats.MonadThrow
import jsonrpclib.fs2._
import smithy4s.Service
import smithy4s.http.json.JCodec
import smithy4s.schema._
import cats.effect.kernel.Async
import smithy4s.kinds.PolyFunction5
import smithy4s.ShapeId
import cats.syntax.all._

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
        val (input, smithy4sEndpoint) = service.endpoint(op)
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
      service.endpoints
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

  def jsonRPCStub[I, E, O, SI, SO](
      smithy4sEndpoint: service.Endpoint[I, E, O, SI, SO],
      endpointSpec: EndpointSpec
  ): I => F[O] = {

    implicit val inputCodec: JCodec[I] = JCodec.fromSchema(smithy4sEndpoint.input)
    implicit val outputCodec: JCodec[O] = JCodec.fromSchema(smithy4sEndpoint.output)

    endpointSpec match {
      case EndpointSpec.Notification(methodName) =>
        val coerce = coerceUnit[O](smithy4sEndpoint.output)
        channel.notificationStub[I](methodName).andThen(_ *> coerce)
      case EndpointSpec.Request(methodName) =>
        channel.simpleStub[I, O](methodName)
    }
  }

  case class NotJsonRPCEndpoint(shapeId: ShapeId) extends Throwable
  case object NotUnitReturnType extends Throwable
  private def coerceUnit[A](schema: Schema[A]): F[A] =
    schema match {
      case Schema.PrimitiveSchema(_, _, Primitive.PUnit) => MonadThrow[F].unit
      case _                                             => MonadThrow[F].raiseError[A](NotUnitReturnType)
    }

}
