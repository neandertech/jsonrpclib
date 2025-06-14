package jsonrpclib.smithy4sinterop

import io.circe.Codec
import jsonrpclib.Endpoint
import jsonrpclib.ErrorEncoder
import jsonrpclib.ErrorPayload
import jsonrpclib.Monadic
import jsonrpclib.Monadic.syntax._
import jsonrpclib.Payload
import smithy4s.kinds.FunctorAlgebra
import smithy4s.kinds.FunctorInterpreter
import smithy4s.schema.ErrorSchema
import smithy4s.Service

import _root_.smithy4s.{Endpoint => Smithy4sEndpoint}

object ServerEndpoints {

  /** Creates JSON-RPC server endpoints from a Smithy service implementation.
    *
    * Given a Smithy `FunctorAlgebra[Alg, F]`, this extracts all operations and compiles them into JSON-RPC
    * `Endpoint[F]` handlers that can be mounted on a communication channel (e.g. `FS2Channel`).
    *
    * Supports both standard request-response and notification-style endpoints, as well as Smithy-modeled errors.
    *
    * Usage:
    * {{{
    * val endpoints = ServerEndpoints(new ServerImpl)
    * channel.withEndpoints(endpoints)
    * }}}
    */
  def apply[Alg[_[_, _, _, _, _]], F[_]](
      impl: FunctorAlgebra[Alg, F]
  )(implicit service: Service[Alg], F: Monadic[F]): List[Endpoint[F]] = {
    val transformedService = JsonRpcTransformations.apply(service)
    val interpreter: transformedService.FunctorInterpreter[F] = transformedService.toPolyFunction(impl)
    val codecCache = CirceJsonCodec.Codec.createCache()
    transformedService.endpoints.toList.flatMap { smithy4sEndpoint =>
      EndpointSpec
        .fromHints(smithy4sEndpoint.hints)
        .map { endpointSpec =>
          jsonRPCEndpoint(smithy4sEndpoint, endpointSpec, interpreter, codecCache)
        }
        .toList
    }
  }

  /** Constructs a JSON-RPC endpoint from a Smithy endpoint definition.
    *
    * Translates a single Smithy4s endpoint into a JSON-RPC `Endpoint[F]`, based on the method name and interaction type
    * described in `EndpointSpec`.
    *
    * @param smithy4sEndpoint
    *   The Smithy4s endpoint to expose over JSON-RPC
    * @param endpointSpec
    *   JSON-RPC method name and interaction hints
    * @param impl
    *   Interpreter that executes the Smithy operation in `F`
    * @param codecCache
    *   Coche for the schema to codec compilation results
    * @return
    *   A JSON-RPC-compatible `Endpoint[F]`
    */
  private def jsonRPCEndpoint[F[_]: Monadic, Op[_, _, _, _, _], I, E, O, SI, SO](
      smithy4sEndpoint: Smithy4sEndpoint[Op, I, E, O, SI, SO],
      endpointSpec: EndpointSpec,
      impl: FunctorInterpreter[Op, F],
      codecCache: CirceJsonCodec.Codec.Cache
  ): Endpoint[F] = {
    implicit val inputCodec: Codec[I] = CirceJsonCodec.Codec.fromSchema(smithy4sEndpoint.input, codecCache)
    implicit val outputCodec: Codec[O] = CirceJsonCodec.Codec.fromSchema(smithy4sEndpoint.output, codecCache)

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
            implicit val errorCodec: ErrorEncoder[E] = errorCodecFromSchema(errorSchema, codecCache)
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

  private def errorCodecFromSchema[A](s: ErrorSchema[A], cache: CirceJsonCodec.Codec.Cache): ErrorEncoder[A] = {
    val circeCodec = CirceJsonCodec.Codec.fromSchema(s.schema, cache)
    (a: A) =>
      ErrorPayload(
        0,
        Option(s.unliftError(a).getMessage()).getOrElse("JSONRPC-smithy4s application error"),
        Some(Payload(circeCodec.apply(a)))
      )
  }
}
