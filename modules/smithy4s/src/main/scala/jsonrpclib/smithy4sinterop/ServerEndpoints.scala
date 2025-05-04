package jsonrpclib.smithy4sinterop

import _root_.smithy4s.{Endpoint => Smithy4sEndpoint}
import cats.MonadThrow
import cats.syntax.all._
import jsonrpclib.Endpoint
import jsonrpclib.fs2._
import smithy4s.Service
import smithy4s.http.json.JCodec
import smithy4s.kinds.FunctorAlgebra
import smithy4s.kinds.FunctorInterpreter

object ServerEndpoints {

  def apply[Alg[_[_, _, _, _, _]], F[_]](
      impl: FunctorAlgebra[Alg, F]
  )(implicit service: Service[Alg], F: MonadThrow[F]): List[Endpoint[F]] = {
    val interpreter: service.FunctorInterpreter[F] = service.toPolyFunction(impl)
    service.endpoints.flatMap { smithy4sEndpoint =>
      EndpointSpec.fromHints(smithy4sEndpoint.hints).map { endpointSpec =>
        jsonRPCEndpoint(smithy4sEndpoint, endpointSpec, interpreter)
      }
    }

  }

  // TODO : codify errors at smithy level and handle them.
  def jsonRPCEndpoint[F[_]: MonadThrow, Op[_, _, _, _, _], I, E, O, SI, SO](
      smithy4sEndpoint: Smithy4sEndpoint[Op, I, E, O, SI, SO],
      endpointSpec: EndpointSpec,
      impl: FunctorInterpreter[Op, F]
  ): Endpoint[F] = {

    implicit val inputCodec: JCodec[I] = JCodec.fromSchema(smithy4sEndpoint.input)
    implicit val outputCodec: JCodec[O] = JCodec.fromSchema(smithy4sEndpoint.output)

    endpointSpec match {
      case EndpointSpec.Notification(methodName) =>
        Endpoint[F](methodName).notification { (input: I) =>
          val op = smithy4sEndpoint.wrap(input)
          impl(op).void
        }
      case EndpointSpec.Request(methodName) =>
        Endpoint[F](methodName).simple { (input: I) =>
          val op = smithy4sEndpoint.wrap(input)
          impl(op)
        }
    }
  }
}
