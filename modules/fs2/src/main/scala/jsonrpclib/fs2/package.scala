package jsonrpclib

import cats.effect.kernel.MonadCancel
import cats.effect.kernel.Resource
import cats.Monad
import cats.MonadThrow

import _root_.fs2.Stream

package object fs2 {

  private[jsonrpclib] implicit class EffectOps[F[_], A](private val fa: F[A]) extends AnyVal {
    def toStream: Stream[F, A] = Stream.eval(fa)
  }

  private[jsonrpclib] implicit class ResourceOps[F[_], A](private val fa: Resource[F, A]) extends AnyVal {
    def asStream(implicit F: MonadCancel[F, Throwable]): Stream[F, A] = Stream.resource(fa)
  }

  implicit def catsMonadic[F[_]: MonadThrow]: Monadic[F] = new Monadic[F] {
    def doFlatMap[A, B](fa: F[A])(f: A => F[B]): F[B] = Monad[F].flatMap(fa)(f)

    def doPure[A](a: A): F[A] = Monad[F].pure(a)

    def doAttempt[A](fa: F[A]): F[Either[Throwable, A]] = MonadThrow[F].attempt(fa)

    def doRaiseError[A](e: Throwable): F[A] = MonadThrow[F].raiseError(e)

    override def doMap[A, B](fa: F[A])(f: A => B): F[B] = Monad[F].map(fa)(f)

    override def doVoid[A](fa: F[A]): F[Unit] = Monad[F].void(fa)
  }

}
