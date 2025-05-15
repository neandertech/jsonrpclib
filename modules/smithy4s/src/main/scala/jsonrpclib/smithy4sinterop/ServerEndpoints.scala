package jsonrpclib.smithy4sinterop

import _root_.smithy4s.{Endpoint => Smithy4sEndpoint}
import jsonrpclib.Endpoint
import smithy4s.Service
import smithy4s.kinds.FunctorAlgebra
import smithy4s.kinds.FunctorInterpreter
import jsonrpclib.Monadic
import jsonrpclib.Payload
import jsonrpclib.ErrorPayload
import io.circe.Codec
import jsonrpclib.Monadic.syntax._
import jsonrpclib.ErrorEncoder
import smithy4s.schema.ErrorSchema

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

  def jsonRPCEndpoint[F[_]: Monadic, Op[_, _, _, _, _], I, E, O, SI, SO](
      smithy4sEndpoint: Smithy4sEndpoint[Op, I, E, O, SI, SO],
      endpointSpec: EndpointSpec,
      impl: FunctorInterpreter[Op, F]
  ): Endpoint[F] = {

    implicit val inputCodec: Codec[I] = CirceJsonCodec.fromSchema(smithy4sEndpoint.input)
    implicit val outputCodec: Codec[O] = CirceJsonCodec.fromSchema(smithy4sEndpoint.output)

    def errorResponse(throwable: Throwable): F[E] = throwable match {
      case smithy4sEndpoint.Error((_, e)) => e.pure
      case e: Throwable                   => e.raiseError
    }

    endpointSpec match {
      case EndpointSpec.Notification(methodName) =>
        Endpoint[F](methodName).notification { (input: I) =>
          val op = smithy4sEndpoint.wrap(input)
          impl(op).void
        }
      case EndpointSpec.Request(methodName) =>
        smithy4sEndpoint.error match {
          case None =>
            Endpoint[F](methodName).simple[I, O] { (input: I) =>
              val op = smithy4sEndpoint.wrap(input)
              impl(op)
            }
          case Some(errorSchema) =>
            implicit val errorCodec: ErrorEncoder[E] = errorCodecFromSchema(errorSchema)
            Endpoint[F](methodName).apply[I, E, O] { (input: I) =>
              val op = smithy4sEndpoint.wrap(input)
              impl(op).attempt.flatMap {
                case Left(err)      => errorResponse(err).map(r => Left(r): Either[E, O])
                case Right(success) => (Right(success): Either[E, O]).pure
              }
            }
        }
    }
  }

  private def errorCodecFromSchema[A](s: ErrorSchema[A]): ErrorEncoder[A] = {
    val circeCodec = CirceJsonCodec.fromSchema(s.schema)
    (a: A) =>
      ErrorPayload(
        0,
        Option(s.unliftError(a).getMessage()).getOrElse("JSONRPC-smithy4s application error"),
        Some(Payload(circeCodec.apply(a)))
      )
  }
}
