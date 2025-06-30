$version: "2.0"

namespace test

use jsonrpclib#jsonRpcNotification
use jsonrpclib#jsonRpc
use jsonrpclib#jsonRpcRequest
use jsonrpclib#jsonRpcPayload

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
    errors: [NotWelcomeError]
}


@jsonRpc
service TestServerWithPayload {
    operations: [GreetWithPayload]
}

@jsonRpcRequest("greetWithPayload")
operation GreetWithPayload {
    input := {
        @required
        @jsonRpcPayload
        payload: GreetInputPayload
    }
    output := {
        @required
        @jsonRpcPayload
        payload: GreetOutputPayload
    }
}

structure GreetInputPayload {
    @required
    name: String
}

structure GreetOutputPayload {
    @required
    message: String
}

@error("client")
structure NotWelcomeError {
    @required
    msg: String
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

@jsonRpc
service WeatherService {
    operations: [GetWeather]
}

@jsonRpcRequest("getWeather")
operation GetWeather {
    input := {
        @required
        city: String
    }
    output := {
        @required
        weather: String
    }
}
