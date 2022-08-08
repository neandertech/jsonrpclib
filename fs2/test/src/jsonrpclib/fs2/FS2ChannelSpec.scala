package jsonrpclib.fs2

import cats.effect.IO
import cats.effect.implicits._
import cats.effect.std.Queue
import cats.syntax.all._
import com.github.plokhotnyuk.jsoniter_scala.core.JsonValueCodec
import com.github.plokhotnyuk.jsoniter_scala.macros.JsonCodecMaker
import fs2.Stream
import jsonrpclib._
import jsonrpclib.fs2.FS2Channel
import weaver._

import scala.concurrent.duration._

object FS2ChannelSpec extends SimpleIOSuite {

  case class IntWrapper(int: Int)
  object IntWrapper {
    implicit val jcodec: JsonValueCodec[IntWrapper] = JsonCodecMaker.make
  }

  case class CancelRequest(callId: CallId)
  object CancelRequest {
    implicit val jcodec: JsonValueCodec[CancelRequest] = JsonCodecMaker.make
  }

  def testRes(name: TestName)(run: Stream[IO, Expectations]): Unit =
    test(name)(run.compile.lastOrError.timeout(10.second))

  testRes("Round trip") {
    val endpoint: Endpoint[IO] = Endpoint[IO]("inc").simple((int: IntWrapper) => IO(IntWrapper(int.int + 1)))

    for {
      stdout <- Queue.bounded[IO, Payload](10).toStream
      stdin <- Queue.bounded[IO, Payload](10).toStream
      serverSideChannel <- FS2Channel[IO](Stream.fromQueueUnterminated(stdin), stdout.offer)
      clientSideChannel <- FS2Channel[IO](Stream.fromQueueUnterminated(stdout), stdin.offer)
      _ <- serverSideChannel.withEndpoint(endpoint).asStream
      remoteFunction = clientSideChannel.simpleStub[IntWrapper, IntWrapper]("inc")
      _ <- serverSideChannel.open.asStream
      _ <- clientSideChannel.open.asStream
      result <- remoteFunction(IntWrapper(1)).toStream
    } yield {
      expect.same(result, IntWrapper(2))
    }
  }

  testRes("Endpoint not mounted") {

    for {
      stdout <- Queue.bounded[IO, Payload](10).toStream
      stdin <- Queue.bounded[IO, Payload](10).toStream
      serverSideChannel <- FS2Channel[IO](Stream.fromQueueUnterminated(stdin), stdout.offer)
      clientSideChannel <- FS2Channel[IO](Stream.fromQueueUnterminated(stdout), stdin.offer)
      remoteFunction = clientSideChannel.simpleStub[IntWrapper, IntWrapper]("inc")
      _ <- serverSideChannel.open.asStream
      _ <- clientSideChannel.open.asStream
      result <- remoteFunction(IntWrapper(1)).attempt.toStream
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
      stdout <- Queue.bounded[IO, Payload](10).toStream
      stdin <- Queue.bounded[IO, Payload](10).toStream
      serverSideChannel <- FS2Channel[IO](Stream.fromQueueUnterminated(stdin), payload => stdout.offer(payload))
      clientSideChannel <- FS2Channel[IO](Stream.fromQueueUnterminated(stdout), payload => stdin.offer(payload))
      _ <- serverSideChannel.withEndpoint(endpoint).asStream
      remoteFunction = clientSideChannel.simpleStub[IntWrapper, IntWrapper]("inc")
      _ <- serverSideChannel.open.asStream
      _ <- clientSideChannel.open.asStream
      timedResults <- (1 to 10).toList.map(IntWrapper(_)).parTraverse(remoteFunction).timed.toStream
    } yield {
      val (time, results) = timedResults
      expect.same(results, (2 to 11).toList.map(IntWrapper(_))) &&
      expect(time < 2.seconds)
    }
  }

  testRes("cancelation propagates") {
    val cancelTemplate = CancelTemplate.make[CancelRequest]("$/cancel", _.callId, CancelRequest(_))

    for {
      canceledPromise <- IO.deferred[IntWrapper].toStream
      endpoint: Endpoint[IO] = Endpoint[IO]("never").simple((int: IntWrapper) =>
        IO.never.as(int).onCancel(canceledPromise.complete(int).void)
      )

      stdin <- Queue.bounded[IO, Payload](10).toStream
      stdout <- Queue.bounded[IO, Payload](10).toStream
      serverSideChannel <- FS2Channel[IO](Stream.fromQueueUnterminated(stdin), stdout.offer, Some(cancelTemplate))
      clientSideChannel <- FS2Channel[IO](Stream.fromQueueUnterminated(stdout), stdin.offer, Some(cancelTemplate))
      _ <- serverSideChannel.withEndpoint(endpoint).asStream
      remoteFunction = clientSideChannel.simpleStub[IntWrapper, IntWrapper]("never")
      _ <- serverSideChannel.open.asStream
      _ <- clientSideChannel.open.asStream
      // Timeing-out client-call to verify cancelation progagates to server
      _ <- IO.race(remoteFunction(IntWrapper(23)), IO.sleep(1.second)).toStream
      result <- canceledPromise.get.toStream
    } yield {
      expect.same(result, IntWrapper(23))
    }
  }

}
