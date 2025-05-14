package jsonrpclib

trait PolyFunction[F[_], G[_]] { self =>
  def apply[A0](fa: F[A0]): G[A0]
}
