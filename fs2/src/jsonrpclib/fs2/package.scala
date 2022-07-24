package jsonrpclib

import cats.MonadThrow
import cats.Monad

package object fs2interop {

  implicit def catsMonadic[F[_]: MonadThrow]: Monadic[F] = new Monadic[F] {
    def doFlatMap[A, B](fa: F[A])(f: A => F[B]): F[B] = Monad[F].flatMap(fa)(f)

    def doPure[A](a: A): F[A] = Monad[F].pure(a)

    def doAttempt[A](fa: F[A]): F[Either[Throwable, A]] = MonadThrow[F].attempt(fa)
  }

}
