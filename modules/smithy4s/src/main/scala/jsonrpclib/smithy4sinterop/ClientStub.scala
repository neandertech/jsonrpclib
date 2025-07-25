package jsonrpclib.smithy4sinterop

import io.circe.Codec
import jsonrpclib.Channel
import jsonrpclib.ErrorPayload
import jsonrpclib.Monadic
import jsonrpclib.Monadic.syntax._
import jsonrpclib.ProtocolError
import smithy4s.~>
import smithy4s.checkProtocol
import smithy4s.schema._
import smithy4s.Service
import smithy4s.ShapeId
import smithy4s.UnsupportedProtocolError

object ClientStub {

  /** Creates a JSON-RPC client implementation for a Smithy service.
    *
    * Given a Smithy `Service[Alg]` and a JSON-RPC communication `Channel[F]`, this constructs a fully functional client
    * that translates method calls into JSON-RPC messages sent over the channel.
    *
    * Before constructing the client, this method checks whether the given Smithy service supports the JSON-RPC
    * protocol. If not, it returns a `Left(UnsupportedProtocolError)`.
    *
    * Supports both standard request-response and fire-and-forget notification endpoints.
    *
    * Usage:
    * {{{
    *   val stubOrError: Either[UnsupportedProtocolError, MyService[IO]] =
    *     ClientStub(myService, myChannel)
    *
    *   val result: IO[Unit] = stubOrError match {
    *     case Right(stub) => stub.hello("world").void
    *     case Left(error) => IO.raiseError(new RuntimeException(error.toString))
    *   }
    * }}}
    *
    * @param service
    *   Smithy service definition
    * @param channel
    *   JSON-RPC communication channel
    * @return
    *   Either an error if the protocol is unsupported or a compiled client implementation
    */
  def apply[Alg[_[_, _, _, _, _]], F[_]: Monadic](
      service: Service[Alg],
      channel: Channel[F]
  ): Either[UnsupportedProtocolError, service.Impl[F]] =
    checkProtocol(service, jsonrpclib.JsonRpc).map(_ =>
      new ClientStub(JsonRpcTransformations.apply(service), channel).compile
    )
}

private class ClientStub[Alg[_[_, _, _, _, _]], F[_]: Monadic](val service: Service[Alg], channel: Channel[F]) {

  def compile: service.Impl[F] = {
    val codecCache = CirceJsonCodec.Codec.createCache()
    val interpreter = new service.FunctorEndpointCompiler[F] {
      def apply[I, E, O, SI, SO](e: service.Endpoint[I, E, O, SI, SO]): I => F[O] = {
        val shapeId = e.id
        val spec = EndpointSpec.fromHints(e.hints).toRight(NotJsonRPCEndpoint(shapeId)).toTry.get

        jsonRPCStub(e, spec, codecCache)
      }
    }

    service.impl(interpreter)
  }

  def jsonRPCStub[I, E, O, SI, SO](
      smithy4sEndpoint: service.Endpoint[I, E, O, SI, SO],
      endpointSpec: EndpointSpec,
      codecCache: CirceJsonCodec.Codec.Cache
  ): I => F[O] = {

    implicit val inputCodec: Codec[I] = CirceJsonCodec.Codec.fromSchema(smithy4sEndpoint.input, codecCache)
    implicit val outputCodec: Codec[O] = CirceJsonCodec.Codec.fromSchema(smithy4sEndpoint.output, codecCache)

    def errorResponse(throwable: Throwable, errorCodec: Codec[E]): F[E] = {
      throwable match {
        case ErrorPayload(_, _, Some(payload)) =>
          errorCodec.decodeJson(payload.data) match {
            case Left(err)    => ProtocolError.ParseError(err.getMessage).raiseError
            case Right(error) => error.pure
          }
        case e: Throwable => e.raiseError
      }
    }

    endpointSpec match {
      case EndpointSpec.Notification(methodName) =>
        val coerce = coerceUnit[O](smithy4sEndpoint.output)
        channel.notificationStub[I](methodName).andThen(f => Monadic[F].doFlatMap(f)(_ => coerce))
      case EndpointSpec.Request(methodName) =>
        smithy4sEndpoint.error match {
          case None => channel.simpleStub[I, O](methodName)
          case Some(errorSchema) =>
            val errorCodec = CirceJsonCodec.Codec.fromSchema(errorSchema.schema, codecCache)
            val stub = channel.simpleStub[I, O](methodName)
            (in: I) =>
              stub.apply(in).attempt.flatMap {
                case Right(success) => success.pure
                case Left(error) =>
                  errorResponse(error, errorCodec)
                    .flatMap(e => errorSchema.unliftError(e).raiseError)
              }
        }
    }
  }

  case class NotJsonRPCEndpoint(shapeId: ShapeId) extends Throwable
  case object NotUnitReturnType extends Throwable

  private object CoerceUnitVisitor extends (Schema ~> F) {
    def apply[A](schema: Schema[A]): F[A] = schema match {
      case s @ Schema.StructSchema(_, _, _, make) if s.isUnit =>
        Monadic[F].doPure(make(IndexedSeq.empty))
      case _ => Monadic[F].doRaiseError[A](NotUnitReturnType)
    }
  }

  private def coerceUnit[A](schema: Schema[A]): F[A] = CoerceUnitVisitor(schema)

}
