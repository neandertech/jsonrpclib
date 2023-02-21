$version: "2.0"

namespace jsonrpclib

/// the JSON-RPC protocol,
/// see https://www.jsonrpc.org/specification
@protocolDefinition(traits: [
    jsonRequest
    jsonNotification
])
@trait(selector: "service")
structure jsonRPC {
}

/// Identifies an operation that abides by request/response semantics
/// https://www.jsonrpc.org/specification#request_object
@trait(selector: "operation")
string jsonRequest

/// Identifies an operation that abides by fire-and-forget semantics
/// see https://www.jsonrpc.org/specification#notification
@trait(selector: "operation")
string jsonNotification