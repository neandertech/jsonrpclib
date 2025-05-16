package jsonrpclib

/** A polymorphic natural transformation from `F[_]` to `G[_]`.
  *
  * @tparam F
  *   Source effect type
  * @tparam G
  *   Target effect type
  */
trait PolyFunction[F[_], G[_]] { self =>
  def apply[A0](fa: => F[A0]): G[A0]
}
