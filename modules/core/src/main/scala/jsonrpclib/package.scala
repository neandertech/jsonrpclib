package object jsonrpclib {

  type ErrorCode = Int
  type ErrorMessage = String

  type ~>[F[_], G[_]] = PolyFunction[F, G]
}
