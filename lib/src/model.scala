package jsonrpclib

sealed trait Result[+E, +A]
object Result {
  final case class Error[E](code: Integer, message: String, data: Option[E]) extends Result[E, Nothing]
  final case class Success[A](value: A) extends Result[Nothing, A]
}
