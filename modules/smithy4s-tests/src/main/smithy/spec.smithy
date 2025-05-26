$version: "2.0"

namespace test

use jsonrpclib#jsonNotification
use jsonrpclib#jsonRPC
use jsonrpclib#jsonRequest
use jsonrpclib#jsonPayload

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


@jsonRPC
service TestServerWithPayload {
    operations: [GreetWithPayload]
}

@jsonRequest("greetWithPayload")
operation GreetWithPayload {
    input := {
        @required
        @jsonPayload
        payload: GreetInputPayload
    }
    output := {
        @required
        @jsonPayload
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

@jsonRPC
service WeatherService {
    operations: [GetWeather]
}

@jsonRequest("getWeather")
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
