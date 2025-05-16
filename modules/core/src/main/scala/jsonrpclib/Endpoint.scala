package jsonrpclib

import io.circe.Decoder
import io.circe.Encoder
import jsonrpclib.ErrorCodec.errorPayloadCodec

/** Represents a JSON-RPC method handler that can be invoked by the server.
  *
  * An `Endpoint[F]` defines how to decode input from a JSON-RPC message, execute some effectful logic, and optionally
  * return a response.
  *
  * The endpoint's `method` field is used to match incoming JSON-RPC requests.
  */
sealed trait Endpoint[F[_]] {

  /** The JSON-RPC method name this endpoint responds to. Used for dispatching incoming requests. */
  def method: String

  /** Transforms the effect type of this endpoint using the provided `PolyFunction`.
    *
    * This allows reinterpreting the endpoint’s logic in a different effect context (e.g., from `IO` to `Kleisli[IO,
    * Ctx, *]`, or from `F` to `EitherT[F, E, *]`).
    *
    * @param f
    *   A polymorphic function that transforms `F[_]` into `G[_]`
    * @return
    *   A new `Endpoint[G]` with the same behavior but in a new effect type
    */
  def mapK[G[_]](f: PolyFunction[F, G]): Endpoint[G]
}

object Endpoint {

  type MethodPattern = String
  type Method = String

  def apply[F[_]](method: Method): PartiallyAppliedEndpoint[F] = new PartiallyAppliedEndpoint[F](method)

  class PartiallyAppliedEndpoint[F[_]](method: MethodPattern) {
    def apply[In, Err, Out](
        run: In => F[Either[Err, Out]]
    )(implicit inCodec: Decoder[In], errEncoder: ErrorEncoder[Err], outCodec: Encoder[Out]): Endpoint[F] =
      RequestResponseEndpoint(method, (_: InputMessage, in: In) => run(in), inCodec, errEncoder, outCodec)

    def full[In, Err, Out](
        run: (InputMessage, In) => F[Either[Err, Out]]
    )(implicit inCodec: Decoder[In], errEncoder: ErrorEncoder[Err], outCodec: Encoder[Out]): Endpoint[F] =
      RequestResponseEndpoint(method, run, inCodec, errEncoder, outCodec)

    def simple[In, Out](
        run: In => F[Out]
    )(implicit F: Monadic[F], inCodec: Decoder[In], outCodec: Encoder[Out]) =
      apply[In, ErrorPayload, Out](in =>
        F.doFlatMap(F.doAttempt(run(in))) {
          case Left(error)  => F.doPure(Left(ErrorPayload(0, error.getMessage(), None)))
          case Right(value) => F.doPure(Right(value))
        }
      )

    def notification[In](run: In => F[Unit])(implicit inCodec: Decoder[In]): Endpoint[F] =
      NotificationEndpoint(method, (_: InputMessage, in: In) => run(in), inCodec)

    def notificationFull[In](run: (InputMessage, In) => F[Unit])(implicit inCodec: Decoder[In]): Endpoint[F] =
      NotificationEndpoint(method, run, inCodec)

  }

  private[jsonrpclib] final case class NotificationEndpoint[F[_], In](
      method: MethodPattern,
      run: (InputMessage, In) => F[Unit],
      inCodec: Decoder[In]
  ) extends Endpoint[F] {

    def mapK[G[_]](f: PolyFunction[F, G]): Endpoint[G] =
      NotificationEndpoint[G, In](method, (msg, in) => f(run(msg, in)), inCodec)
  }

  private[jsonrpclib] final case class RequestResponseEndpoint[F[_], In, Err, Out](
      method: Method,
      run: (InputMessage, In) => F[Either[Err, Out]],
      inCodec: Decoder[In],
      errEncoder: ErrorEncoder[Err],
      outCodec: Encoder[Out]
  ) extends Endpoint[F] {

    def mapK[G[_]](f: PolyFunction[F, G]): Endpoint[G] =
      RequestResponseEndpoint[G, In, Err, Out](method, (msg, in) => f(run(msg, in)), inCodec, errEncoder, outCodec)
  }
}
