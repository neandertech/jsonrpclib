package examples.server

import jsonrpclib.CallId
import jsonrpclib.fs2._
import cats.effect._
import fs2.io._
import com.github.plokhotnyuk.jsoniter_scala.core.JsonValueCodec
import com.github.plokhotnyuk.jsoniter_scala.macros.JsonCodecMaker
import jsonrpclib.Endpoint

object ServerMain extends IOApp.Simple {

  // Reserving a method for cancelation.
  val cancelTemplate = CancelTemplate.make[CallId]("$/cancel", identity, identity)

  // Creating a datatype that'll serve as a request (and response) of an endpoint
  case class IntWrapper(value: Int)
  object IntWrapper {
    implicit val jcodec: JsonValueCodec[IntWrapper] = JsonCodecMaker.make
  }

  // Implementing an incrementation endpoint
  val increment = Endpoint[IO]("increment").simple { in: IntWrapper =>
    IO.consoleForIO.errorln(s"Server received $in") >>
      IO.pure(in.copy(value = in.value + 1))
  }

  def run: IO[Unit] = {
    // Using errorln as stdout is used by the RPC channel
    IO.consoleForIO.errorln("Starting server") >>
      FS2Channel
        .lspCompliant[IO](fs2.io.stdin[IO](bufSize = 512), fs2.io.stdout[IO])
        .flatMap(_.withEndpointStream(increment)) // mounting an endpoint onto the channel
        .flatMap(_.openStreamForever) // starts the communication
        .compile
        .drain
        .guarantee(IO.consoleForIO.errorln("Terminating server"))
  }

}
