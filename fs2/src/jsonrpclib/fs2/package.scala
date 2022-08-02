package jsonrpclib

import _root_.fs2.Stream
import cats.MonadThrow
import cats.Monad

package object fs2 {

  private[jsonrpclib] implicit class EffectOps[F[_], A](private val fa: F[A]) extends AnyVal {
    def toStream: Stream[F, A] = Stream.eval(fa)
  }

  implicit def catsMonadic[F[_]: MonadThrow]: Monadic[F] = new Monadic[F] {
    def doFlatMap[A, B](fa: F[A])(f: A => F[B]): F[B] = Monad[F].flatMap(fa)(f)

    def doPure[A](a: A): F[A] = Monad[F].pure(a)

    def doAttempt[A](fa: F[A]): F[Either[Throwable, A]] = MonadThrow[F].attempt(fa)

    def doRaiseError[A](e: Throwable): F[A] = MonadThrow[F].raiseError(e)
  }

}
