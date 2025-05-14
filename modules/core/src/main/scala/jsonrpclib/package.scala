package object jsonrpclib {

  type ErrorCode = Int
  type ErrorMessage = String

  private[jsonrpclib] type ~>[F[_], G[_]] = jsonrpclib.PolyFunction[F, G]

}
