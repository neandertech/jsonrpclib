package jsonrpclib.smithy4sinterop

import cats.effect.IO
import cats.syntax.all._
import fs2.concurrent.SignallingRef
import fs2.Stream
import io.circe.Decoder
import io.circe.Encoder
import jsonrpclib.fs2._
import jsonrpclib.ErrorPayload
import jsonrpclib.Monadic
import jsonrpclib.Payload
import smithy4s.kinds.FunctorAlgebra
import smithy4s.Service
import test.GetWeatherInput
import test.GetWeatherOutput
import test.GreetInput
import test.GreetOutput
import test.NotWelcomeError
import test.PingInput
import test.TestClient
import test.TestServer
import test.TestServerOperation
import test.TestServerOperation.GreetError
import test.WeatherService
import weaver._

import scala.concurrent.duration._

object TestServerSpec extends SimpleIOSuite {
  def testRes(name: TestName)(run: Stream[IO, Expectations]): Unit =
    test(name)(run.compile.lastOrError.timeout(10.second))

  type ClientSideChannel = FS2Channel[IO]

  class ServerImpl(client: TestClient[IO]) extends TestServer[IO] {
    def greet(name: String): IO[GreetOutput] = IO.pure(GreetOutput(s"Hello $name"))

    def ping(ping: String): IO[Unit] = {
      client.pong(s"Returned to sender: $ping")
    }
  }

  class Client(ref: SignallingRef[IO, Option[String]]) extends TestClient[IO] {
    def pong(pong: String): IO[Unit] = ref.set(Some(pong))
  }

  trait AlgebraWrapper {
    type Alg[_[_, _, _, _, _]]

    def algebra: FunctorAlgebra[Alg, IO]
    def service: Service[Alg]
  }

  object AlgebraWrapper {
    def apply[A[_[_, _, _, _, _]]](alg: FunctorAlgebra[A, IO])(implicit srv: Service[A]): AlgebraWrapper =
      new AlgebraWrapper {
        type Alg[F[_, _, _, _, _]] = A[F]

        val algebra = alg
        val service = srv
      }
  }

  def setup(mkServer: FS2Channel[IO] => AlgebraWrapper) =
    setupAux(None, mkServer.andThen(Seq(_)), _ => Seq.empty)

  def setup(mkServer: FS2Channel[IO] => AlgebraWrapper, mkClient: FS2Channel[IO] => AlgebraWrapper) =
    setupAux(None, mkServer.andThen(Seq(_)), mkClient.andThen(Seq(_)))

  def setup[Alg[_[_, _, _, _, _]]](
      cancelTemplate: CancelTemplate,
      mkServer: FS2Channel[IO] => Seq[AlgebraWrapper],
      mkClient: FS2Channel[IO] => Seq[AlgebraWrapper]
  ) = setupAux(Some(cancelTemplate), mkServer, mkClient)

  def setupAux[Alg[_[_, _, _, _, _]]](
      cancelTemplate: Option[CancelTemplate],
      mkServer: FS2Channel[IO] => Seq[AlgebraWrapper],
      mkClient: FS2Channel[IO] => Seq[AlgebraWrapper]
  ): Stream[IO, ClientSideChannel] = {
    for {
      serverSideChannel <- FS2Channel.stream[IO](cancelTemplate = cancelTemplate)
      clientSideChannel <- FS2Channel.stream[IO](cancelTemplate = cancelTemplate)
      serverChannelWithEndpoints <- serverSideChannel.withEndpointsStream(mkServer(serverSideChannel).flatMap { p =>
        ServerEndpoints(p.algebra)(p.service, Monadic[IO])
      })
      clientChannelWithEndpoints <- clientSideChannel.withEndpointsStream(mkClient(clientSideChannel).flatMap { p =>
        ServerEndpoints(p.algebra)(p.service, Monadic[IO])
      })
      _ <- Stream(())
        .concurrently(clientChannelWithEndpoints.output.through(serverChannelWithEndpoints.input))
        .concurrently(serverChannelWithEndpoints.output.through(clientChannelWithEndpoints.input))
    } yield {
      clientSideChannel
    }
  }

  testRes("Round trip") {
    implicit val greetInputEncoder: Encoder[GreetInput] = CirceJsonCodec.fromSchema
    implicit val greetOutputDecoder: Decoder[GreetOutput] = CirceJsonCodec.fromSchema

    for {
      clientSideChannel <- setup(channel => {
        val testClient = ClientStub(TestClient, channel)
        AlgebraWrapper(new ServerImpl(testClient))
      })
      remoteFunction = clientSideChannel.simpleStub[GreetInput, GreetOutput]("greet")
      result <- remoteFunction(GreetInput("Bob")).toStream
    } yield {
      expect.same(result.message, "Hello Bob")
    }
  }

  testRes("notification both ways") {
    implicit val greetInputEncoder: Encoder[PingInput] = CirceJsonCodec.fromSchema

    for {
      ref <- SignallingRef[IO, Option[String]](none).toStream
      clientSideChannel <- setup(
        channel => {
          val testClient = ClientStub(TestClient, channel)
          AlgebraWrapper(new ServerImpl(testClient))
        },
        _ => AlgebraWrapper(new Client(ref))
      )
      remoteFunction = clientSideChannel.notificationStub[PingInput]("ping")
      _ <- remoteFunction(PingInput("hi server")).toStream
      result <- ref.discrete.dropWhile(_.isEmpty).take(1)
    } yield {
      expect.same(result, "Returned to sender: hi server".some)
    }
  }

  testRes("internal error when processing notification should not break the server") {
    implicit val greetInputEncoder: Encoder[PingInput] = CirceJsonCodec.fromSchema

    for {
      ref <- SignallingRef[IO, Option[String]](none).toStream
      clientSideChannel <- setup(
        channel => {
          val testClient = ClientStub(TestClient, channel)
          AlgebraWrapper(new TestServer[IO] {
            override def greet(name: String): IO[GreetOutput] = ???

            override def ping(ping: String): IO[Unit] = {
              if (ping == "fail") IO.raiseError(new RuntimeException("throwing internal error on demand"))
              else testClient.pong("pong")
            }
          })
        },
        _ => AlgebraWrapper(new Client(ref))
      )
      remoteFunction = clientSideChannel.notificationStub[PingInput]("ping")
      _ <- remoteFunction(PingInput("fail")).toStream
      _ <- remoteFunction(PingInput("ping")).toStream
      result <- ref.discrete.dropWhile(_.isEmpty).take(1)
    } yield {
      expect.same(result, "pong".some)
    }
  }

  testRes("server returns known error") {
    implicit val greetInputEncoder: Encoder[GreetInput] = CirceJsonCodec.fromSchema
    implicit val greetOutputDecoder: Decoder[GreetOutput] = CirceJsonCodec.fromSchema
    implicit val greetErrorEncoder: Encoder[TestServerOperation.GreetError] = CirceJsonCodec.fromSchema

    for {
      clientSideChannel <- setup(_ => {
        AlgebraWrapper(new TestServer[IO] {
          override def greet(name: String): IO[GreetOutput] = IO.raiseError(NotWelcomeError(s"$name is not welcome"))

          override def ping(ping: String): IO[Unit] = ???
        })
      })
      remoteFunction = clientSideChannel.simpleStub[GreetInput, GreetOutput]("greet")
      result <- remoteFunction(GreetInput("Alice")).attempt.toStream
    } yield {
      matches(result) { case Left(t: ErrorPayload) =>
        expect.same(t.code, 0) &&
        expect.same(t.message, "test.NotWelcomeError(Alice is not welcome)") &&
        expect.same(
          t.data,
          Payload(greetErrorEncoder.apply(GreetError.notWelcomeError(NotWelcomeError(s"Alice is not welcome")))).some
        )
      }
    }
  }

  testRes("server returns unknown error") {
    implicit val greetInputEncoder: Encoder[GreetInput] = CirceJsonCodec.fromSchema
    implicit val greetOutputDecoder: Decoder[GreetOutput] = CirceJsonCodec.fromSchema

    for {
      clientSideChannel <- setup(_ => {
        AlgebraWrapper(new TestServer[IO] {
          override def greet(name: String): IO[GreetOutput] = IO.raiseError(new RuntimeException("some other error"))

          override def ping(ping: String): IO[Unit] = ???
        })
      })
      remoteFunction = clientSideChannel.simpleStub[GreetInput, GreetOutput]("greet")
      result <- remoteFunction(GreetInput("Alice")).attempt.toStream
    } yield {
      matches(result) { case Left(t: ErrorPayload) =>
        expect.same(t.code, 0) &&
        expect.same(t.message, "ServerInternalError: some other error") &&
        expect.same(t.data, none)
      }
    }
  }

  testRes("accessing endpoints from multiple servers") {
    class WeatherServiceImpl() extends WeatherService[IO] {
      override def getWeather(city: String): IO[GetWeatherOutput] = IO(GetWeatherOutput("sunny"))
    }

    for {
      clientSideChannel <- setupAux(
        None,
        channel => {
          val testClient = ClientStub(TestClient, channel)
          Seq(AlgebraWrapper(new ServerImpl(testClient)), AlgebraWrapper(new WeatherServiceImpl()))
        },
        _ => Seq.empty
      )
      greetResult <- {
        implicit val inputEncoder: Encoder[GreetInput] = CirceJsonCodec.fromSchema
        implicit val outputDecoder: Decoder[GreetOutput] = CirceJsonCodec.fromSchema
        val remoteFunction = clientSideChannel.simpleStub[GreetInput, GreetOutput]("greet")
        remoteFunction(GreetInput("Bob")).toStream
      }
      getWeatherResult <- {
        implicit val inputEncoder: Encoder[GetWeatherInput] = CirceJsonCodec.fromSchema
        implicit val outputDecoder: Decoder[GetWeatherOutput] = CirceJsonCodec.fromSchema
        val remoteFunction = clientSideChannel.simpleStub[GetWeatherInput, GetWeatherOutput]("getWeather")
        remoteFunction(GetWeatherInput("Warsaw")).toStream
      }
    } yield {
      expect.same(greetResult.message, "Hello Bob") &&
      expect.same(getWeatherResult.weather, "sunny")
    }
  }
}
