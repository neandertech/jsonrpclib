package jsonrpclib.smithy4sinterop

import smithy4s.Hints

sealed trait EndpointSpec
object EndpointSpec {
  case class Notification(methodName: String) extends EndpointSpec
  case class Request(methodName: String) extends EndpointSpec

  def fromHints(hints: Hints): Option[EndpointSpec] = hints match {
    case jsonrpclib.JsonRequest.hint(r)      => Some(Request(r.value))
    case jsonrpclib.JsonNotification.hint(r) => Some(Notification(r.value))
    case _                                   => None
  }
}
