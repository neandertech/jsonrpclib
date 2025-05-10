package jsonrpclib.smithy4sinterop

import _root_.smithy4s.{Endpoint => Smithy4sEndpoint}
import jsonrpclib.Endpoint
import smithy4s.Service
import smithy4s.kinds.FunctorAlgebra
import smithy4s.kinds.FunctorInterpreter
import smithy4s.json.Json
import smithy4s.schema.Schema
import com.github.plokhotnyuk.jsoniter_scala.core._
import jsonrpclib.Monadic

object ServerEndpoints {

  def apply[Alg[_[_, _, _, _, _]], F[_]](
      impl: FunctorAlgebra[Alg, F]
  )(implicit service: Service[Alg], F: Monadic[F]): List[Endpoint[F]] = {
    val interpreter: service.FunctorInterpreter[F] = service.toPolyFunction(impl)
    service.endpoints.toList.flatMap { smithy4sEndpoint =>
      EndpointSpec
        .fromHints(smithy4sEndpoint.hints)
        .map { endpointSpec =>
          jsonRPCEndpoint(smithy4sEndpoint, endpointSpec, interpreter)
        }
        .toList
    }
  }

  private val jsoniterCodecGlobalCache = Json.jsoniter.createCache()

  private def deriveJsonCodec[A](schema: Schema[A]): JsonCodec[A] =
    Json.jsoniter.fromSchema(schema, jsoniterCodecGlobalCache)

  // TODO : codify errors at smithy level and handle them.
  def jsonRPCEndpoint[F[_]: Monadic, Op[_, _, _, _, _], I, E, O, SI, SO](
      smithy4sEndpoint: Smithy4sEndpoint[Op, I, E, O, SI, SO],
      endpointSpec: EndpointSpec,
      impl: FunctorInterpreter[Op, F]
  ): Endpoint[F] = {

    implicit val inputCodec: JsonCodec[I] = deriveJsonCodec(smithy4sEndpoint.input)
    implicit val outputCodec: JsonCodec[O] = deriveJsonCodec(smithy4sEndpoint.output)

    endpointSpec match {
      case EndpointSpec.Notification(methodName) =>
        Endpoint[F](methodName).notification { (input: I) =>
          val op = smithy4sEndpoint.wrap(input)
          Monadic[F].doVoid(impl(op))
        }
      case EndpointSpec.Request(methodName) =>
        Endpoint[F](methodName).simple { (input: I) =>
          val op = smithy4sEndpoint.wrap(input)
          impl(op)
        }
    }
  }
}
