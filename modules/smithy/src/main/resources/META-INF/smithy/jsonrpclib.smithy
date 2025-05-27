$version: "2.0"

namespace jsonrpclib

/// the JSON-RPC protocol,
/// see https://www.jsonrpc.org/specification
@protocolDefinition(traits: [
    jsonRequest
    jsonNotification
    jsonPayload
    smithy.api#jsonName
    smithy.api#length
    smithy.api#pattern
    smithy.api#range
    smithy.api#required
    smithy.api#timestampFormat
    alloy#uuidFormat
    alloy#discriminated
    alloy#nullable
    alloy#untagged
])
@trait(selector: "service")
structure jsonRPC {
}

/// Identifies an operation that abides by request/response semantics
/// https://www.jsonrpc.org/specification#request_object
@trait(selector: "operation", conflicts: [jsonNotification])
string jsonRequest

/// Identifies an operation that abides by fire-and-forget semantics
/// see https://www.jsonrpc.org/specification#notification
@trait(selector: "operation", conflicts: [jsonRequest])
string jsonNotification


/// Binds a single structure member to the payload of a jsonrpc message.
/// Just like @httpPayload, but for jsonRPC.
@trait(selector: "structure > member", structurallyExclusive: "member")
structure jsonPayload {}
