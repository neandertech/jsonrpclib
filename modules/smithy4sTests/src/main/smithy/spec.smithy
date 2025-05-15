$version: "2.0"

namespace test

use jsonrpclib#jsonNotification
use jsonrpclib#jsonRPC
use jsonrpclib#jsonRequest

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
    errors: [NotWelcomeError]
}

@error("client")
structure NotWelcomeError {
    @required
    msg: String
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
