package jsonrpclib

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

trait Monadic[F[_]] {
  def doFlatMap[A, B](fa: F[A])(f: A => F[B]): F[B]
  def doPure[A](a: A): F[A]
  def doAttempt[A](fa: F[A]): F[Either[Throwable, A]]
  def doRaiseError[A](e: Throwable): F[A]
  def doMap[A, B](fa: F[A])(f: A => B): F[B] = doFlatMap(fa)(a => doPure(f(a)))
  def doVoid[A](fa: F[A]): F[Unit] = doMap(fa)(_ => ())
}

object Monadic {
  def apply[F[_]](implicit F: Monadic[F]): Monadic[F] = F

  implicit def monadicFuture(implicit ec: ExecutionContext): Monadic[Future] = new Monadic[Future] {
    def doFlatMap[A, B](fa: Future[A])(f: A => Future[B]): Future[B] = fa.flatMap(f)

    def doPure[A](a: A): Future[A] = Future.successful(a)

    def doAttempt[A](fa: Future[A]): Future[Either[Throwable, A]] = fa.map(Right(_)).recover(Left(_))

    def doRaiseError[A](e: Throwable): Future[A] = Future.failed(e)

    override def doMap[A, B](fa: Future[A])(f: A => B): Future[B] = fa.map(f)
  }

  object syntax {
    implicit final class MonadicOps[F[_], A](private val fa: F[A]) extends AnyVal {
      def flatMap[B](f: A => F[B])(implicit m: Monadic[F]): F[B] = m.doFlatMap(fa)(f)
      def map[B](f: A => B)(implicit m: Monadic[F]): F[B] = m.doMap(fa)(f)
      def attempt(implicit m: Monadic[F]): F[Either[Throwable, A]] = m.doAttempt(fa)
      def void(implicit m: Monadic[F]): F[Unit] = m.doVoid(fa)
    }
    implicit final class MonadicOpsPure[A](private val a: A) extends AnyVal {
      def pure[F[_]](implicit m: Monadic[F]): F[A] = m.doPure(a)
    }
    implicit final class MonadicOpsThrowable(private val t: Throwable) extends AnyVal {
      def raiseError[F[_], A](implicit m: Monadic[F]): F[A] = m.doRaiseError(t)
    }
  }
}
