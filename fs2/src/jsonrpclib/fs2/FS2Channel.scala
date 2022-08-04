package jsonrpclib
package fs2

import _root_.fs2.Pipe
import _root_.fs2.Stream
import cats.Applicative
import cats.Functor
import cats.Monad
import cats.MonadThrow
import cats.effect.kernel._
import cats.effect.std.Supervisor
import cats.syntax.all._
import jsonrpclib.internals.MessageDispatcher
import jsonrpclib.internals._

import scala.util.Try
import _root_.fs2.concurrent.SignallingRef

trait FS2Channel[F[_]] extends Channel[F] {
  def withEndpoint(endpoint: Endpoint[F])(implicit F: Functor[F]): Resource[F, Unit] =
    Resource.make(mountEndpoint(endpoint))(_ => unmountEndpoint(endpoint.method))

  def withEndpoints(endpoint: Endpoint[F], rest: Endpoint[F]*)(implicit F: Monad[F]): Resource[F, Unit] =
    (endpoint :: rest.toList).traverse_(withEndpoint)

  def open: Resource[F, Unit]
  def openStream: Stream[F, Unit]
}

object FS2Channel {

  def lspCompliant[F[_]: Concurrent](
      byteStream: Stream[F, Byte],
      byteSink: Pipe[F, Byte, Nothing],
      startingEndpoints: List[Endpoint[F]] = List.empty,
      bufferSize: Int = 512
  ): Stream[F, FS2Channel[F]] = internals.LSP.writeSink(byteSink, bufferSize).flatMap { sink =>
    apply[F](internals.LSP.readStream(byteStream), sink, startingEndpoints)
  }

  def apply[F[_]: Concurrent](
      payloadStream: Stream[F, Payload],
      payloadSink: Payload => F[Unit],
      startingEndpoints: List[Endpoint[F]] = List.empty[Endpoint[F]]
  ): Stream[F, FS2Channel[F]] = {
    val endpointsMap = startingEndpoints.map(ep => ep.method -> ep).toMap
    for {
      supervisor <- Stream.resource(Supervisor[F])
      ref <- Ref[F].of(State[F](Map.empty, endpointsMap, 0, false)).toStream
      isOpen <- SignallingRef[F].of(false).toStream
      awaitingSink = isOpen.waitUntil(identity) >> payloadSink(_: Payload)
      impl = new Impl(awaitingSink, ref, isOpen, supervisor)
      _ <- Stream(()).concurrently {
        // Gatekeeping the pull until the channel is actually marked as open
        val wait = isOpen.waitUntil(identity)
        payloadStream.evalTap(_ => wait).evalMap(impl.handleReceivedPayload)
      }
    } yield impl
  }

  private case class State[F[_]](
      pendingCalls: Map[CallId, OutputMessage => F[Unit]],
      endpoints: Map[String, Endpoint[F]],
      counter: Long,
      isOpen: Boolean
  ) {
    def nextCallId: (State[F], CallId) = (this.copy(counter = counter + 1), CallId.NumberId(counter))
    def storePendingCall(callId: CallId, handle: OutputMessage => F[Unit]): State[F] =
      this.copy(pendingCalls = pendingCalls + (callId -> handle))
    def removePendingCall(callId: CallId): (State[F], Option[OutputMessage => F[Unit]]) = {
      val result = pendingCalls.get(callId)
      (this.copy(pendingCalls = pendingCalls.removed(callId)), result)
    }
    def mountEndpoint(endpoint: Endpoint[F]): Either[ConflictingMethodError, State[F]] =
      endpoints.get(endpoint.method) match {
        case None    => Right(this.copy(endpoints = endpoints + (endpoint.method -> endpoint)))
        case Some(_) => Left(ConflictingMethodError(endpoint.method))
      }
    def removeEndpoint(method: String): State[F] =
      copy(endpoints = endpoints.removed(method))

    def open: State[F] = copy(isOpen = true)
    def close: State[F] = copy(isOpen = false)
  }

  private class Impl[F[_]](
      private val sink: Payload => F[Unit],
      private val state: Ref[F, FS2Channel.State[F]],
      private val isOpen: SignallingRef[F, Boolean],
      supervisor: Supervisor[F]
  )(implicit F: Concurrent[F])
      extends MessageDispatcher[F]
      with FS2Channel[F] {

    def mountEndpoint(endpoint: Endpoint[F]): F[Unit] = state
      .modify(s =>
        s.mountEndpoint(endpoint) match {
          case Left(error)  => (s, MonadThrow[F].raiseError[Unit](error))
          case Right(value) => (value, Applicative[F].unit)
        }
      )
      .flatMap(identity)

    def unmountEndpoint(method: String): F[Unit] = state.update(_.removeEndpoint(method))

    def open: Resource[F, Unit] = Resource.make[F, Unit](isOpen.set(true))(_ => isOpen.set(false))
    def openStream: Stream[F, Unit] = Stream.resource(open)

    protected def background[A](fa: F[A]): F[Unit] = supervisor.supervise(fa).void
    protected def reportError(params: Option[Payload], error: ProtocolError, method: String): F[Unit] = ???
    protected def getEndpoint(method: String): F[Option[Endpoint[F]]] = state.get.map(_.endpoints.get(method))
    protected def sendMessage(message: Message): F[Unit] = sink(Codec.encode(message))
    protected def nextCallId(): F[CallId] = state.modify(_.nextCallId)
    protected def createPromise[A](): F[(Try[A] => F[Unit], () => F[A])] = Deferred[F, Try[A]].map { promise =>
      def compile(trya: Try[A]): F[Unit] = promise.complete(trya).void
      def get(): F[A] = promise.get.flatMap(_.liftTo[F])
      (compile(_), get)
    }
    protected def storePendingCall(callId: CallId, handle: OutputMessage => F[Unit]): F[Unit] =
      state.update(_.storePendingCall(callId, handle))
    protected def removePendingCall(callId: CallId): F[Option[OutputMessage => F[Unit]]] =
      state.modify(_.removePendingCall(callId))

  }
}
