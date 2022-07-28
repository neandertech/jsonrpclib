package jsonrpclib.fs2interop

import weaver._
import jsonrpclib._
import jsonrpclib.fs2interop.FS2Channel
import cats.effect.IO
import cats.effect.kernel.Ref
import cats.data.Chain
import cats.effect.std.Queue
import jsonrpclib.Payload
import jsonrpclib.Endpoint
import cats.effect.implicits._
import scala.concurrent.duration._
import cats.syntax.all._

import com.github.plokhotnyuk.jsoniter_scala.macros.JsonCodecMaker
import com.github.plokhotnyuk.jsoniter_scala.core.JsonValueCodec
import jsonrpclib.Codec
import cats.effect.kernel.Resource

object FS2ChannelSpec extends SimpleIOSuite {

  case class IntWrapper(int: Int)
  object IntWrapper {
    implicit val jcodec: JsonValueCodec[IntWrapper] = JsonCodecMaker.make
  }

  def testRes(name: TestName)(run: Resource[IO, Expectations]): Unit =
    test(name)(run.use(e => IO.pure(e)))

  testRes("Round trip") {
    val endpoint: Endpoint[IO] = Endpoint[IO]("inc").simple((int: IntWrapper) => IO(IntWrapper(int.int + 1)))

    for {
      stdout <- Queue.bounded[IO, Payload](10).toResource
      stdin <- Queue.bounded[IO, Payload](10).toResource
      serverSideChannel <- FS2Channel[IO](fs2.Stream.fromQueueUnterminated(stdin), stdout.offer)
      clientSideChannel <- FS2Channel[IO](fs2.Stream.fromQueueUnterminated(stdout), stdin.offer)
      _ <- serverSideChannel.withEndpoint(endpoint)
      remoteFunction = clientSideChannel.simpleStub[IntWrapper, IntWrapper]("inc")
      result <- remoteFunction(IntWrapper(1)).toResource
    } yield {
      expect.same(result, IntWrapper(2))
    }
  }

  testRes("Endpoint not mounted") {

    for {
      stdout <- Queue.bounded[IO, Payload](10).toResource
      stdin <- Queue.bounded[IO, Payload](10).toResource
      serverSideChannel <- FS2Channel[IO](fs2.Stream.fromQueueUnterminated(stdin), stdout.offer)
      clientSideChannel <- FS2Channel[IO](fs2.Stream.fromQueueUnterminated(stdout), stdin.offer)
      remoteFunction = clientSideChannel.simpleStub[IntWrapper, IntWrapper]("inc")
      result <- remoteFunction(IntWrapper(1)).attempt.toResource
    } yield {
      expect.same(result, Left(ErrorPayload(-32601, "Method inc not found", None)))
    }
  }

  testRes("Concurrency") {

    val endpoint: Endpoint[IO] =
      Endpoint[IO]("inc").simple { (int: IntWrapper) =>
        IO.sleep((1000 - int.int * 100).millis) >> IO(IntWrapper(int.int + 1))
      }

    for {
      stdout <- Queue.bounded[IO, Payload](10).toResource
      stdin <- Queue.bounded[IO, Payload](10).toResource
      serverSideChannel <- FS2Channel[IO](fs2.Stream.fromQueueUnterminated(stdin), payload => stdout.offer(payload))
      clientSideChannel <- FS2Channel[IO](fs2.Stream.fromQueueUnterminated(stdout), payload => stdin.offer(payload))
      _ <- serverSideChannel.withEndpoint(endpoint)
      remoteFunction = clientSideChannel.simpleStub[IntWrapper, IntWrapper]("inc")
      timedResults <- (1 to 10).toList.map(IntWrapper(_)).parTraverse(remoteFunction).toResource.timed
    } yield {
      val (time, results) = timedResults
      expect.same(results, (2 to 11).toList.map(IntWrapper(_))) &&
      expect(time < 2.seconds)
    }
  }

}
