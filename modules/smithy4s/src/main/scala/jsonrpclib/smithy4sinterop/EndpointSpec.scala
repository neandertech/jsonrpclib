package jsonrpclib.smithy4sinterop

import smithy4s.Hints

private[smithy4sinterop] sealed trait EndpointSpec
private[smithy4sinterop] object EndpointSpec {
  case class Notification(methodName: String) extends EndpointSpec
  case class Request(methodName: String) extends EndpointSpec

  def fromHints(hints: Hints): Option[EndpointSpec] = hints match {
    case jsonrpclib.JsonRpcRequest.hint(r)      => Some(Request(r.value))
    case jsonrpclib.JsonRpcNotification.hint(r) => Some(Notification(r.value))
    case _                                      => None
  }
}
