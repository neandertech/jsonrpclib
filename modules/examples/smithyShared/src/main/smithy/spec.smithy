$version: "2.0"

namespace test

use jsonrpclib#jsonRequest
use jsonrpclib#jsonRPC
use jsonrpclib#jsonNotification

@jsonRPC
service TestServer {
  operations: [Greet, Ping]
}

@jsonRPC
service TestClient {
  operations: [Pong]
}

@jsonRequest("greet")
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

@jsonNotification("ping")
operation Ping {
  input := {
    @required
    ping: String
  }
}

@jsonNotification("pong")
operation Pong {
  input := {
    @required
    pong: String
  }
}
