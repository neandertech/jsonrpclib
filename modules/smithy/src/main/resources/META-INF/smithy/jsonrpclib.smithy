$version: "2.0"

namespace jsonrpclib

/// the JSON-RPC protocol,
/// see https://www.jsonrpc.org/specification
@protocolDefinition(traits: [
    jsonRpcRequest
    jsonRpcNotification
    jsonRpcPayload
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
structure jsonRpc {
}

/// Identifies an operation that abides by request/response semantics
/// https://www.jsonrpc.org/specification#request_object
@trait(selector: "operation", conflicts: [jsonRpcNotification])
string jsonRpcRequest

/// Identifies an operation that abides by fire-and-forget semantics
/// see https://www.jsonrpc.org/specification#notification
@trait(selector: "operation", conflicts: [jsonRpcRequest])
string jsonRpcNotification


/// Binds a single structure member to the payload of a jsonrpc message.
/// Just like @httpPayload, but for jsonRpc.
@trait(selector: "structure > member", structurallyExclusive: "member")
@traitValidators({
    "jsonRpcPayload.OnlyTopLevel": { 
        message: "jsonRpcPayload can only be used on the top level of an operation input/output/error.", 
        severity: "ERROR", 
        selector: "$allowedShapes(:root(operation -[input, output, error]-> structure > member)) :not(:in(${allowedShapes}))"
    }
})
structure jsonRpcPayload {}
