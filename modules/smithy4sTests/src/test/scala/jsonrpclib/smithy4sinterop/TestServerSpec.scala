package jsonrpclib.smithy4sinterop

import cats.effect.IO
import fs2.Stream
import test.TestServer
import test.TestClient
import weaver._
import smithy4s.kinds.FunctorAlgebra
import cats.syntax.all._

import scala.concurrent.duration._
import jsonrpclib.fs2._
import test.GreetOutput
import io.circe.Encoder
import test.GreetInput
import io.circe.Decoder
import smithy4s.Service
import jsonrpclib.Monadic
import test.PingInput
import fs2.concurrent.SignallingRef

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
    implicit val greetInputEncoder: Encoder[GreetInput] = CirceJson.fromSchema
    implicit val greetOutputDecoder: Decoder[GreetOutput] = CirceJson.fromSchema

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
    implicit val greetInputEncoder: Encoder[PingInput] = CirceJson.fromSchema

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
}
