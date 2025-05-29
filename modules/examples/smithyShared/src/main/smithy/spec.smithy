$version: "2.0"

namespace test

use jsonrpclib#jsonRpcRequest
use jsonrpclib#jsonRpc
use jsonrpclib#jsonRpcNotification

@jsonRpc
service TestServer {
  operations: [Greet, Ping]
}

@jsonRpc
service TestClient {
  operations: [Pong]
}

@jsonRpcRequest("greet")
operation Greet {
  input := {
    @required
    name: String
  }
  output := {
    @required
    message: String
  }
}

@jsonRpcNotification("ping")
operation Ping {
  input := {
    @required
    ping: String
  }
}

@jsonRpcNotification("pong")
operation Pong {
  input := {
    @required
    pong: String
  }
}
