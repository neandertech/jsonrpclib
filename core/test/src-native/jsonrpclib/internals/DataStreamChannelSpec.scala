package jsonrpclib
package internals

import munit.FunSuite
import java.io.ByteArrayInputStream
import java.io.BufferedReader
import java.io.InputStreamReader
import jsonrpclib.ProtocolError
import java.io.IOException
import java.io.UncheckedIOException
import com.github.plokhotnyuk.jsoniter_scala.core._
import com.github.plokhotnyuk.jsoniter_scala.macros.JsonCodecMaker
import scala.concurrent.Future

import scala.concurrent.ExecutionContext.Implicits.global
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import scala.util.control.NonFatal
import java.io.InputStream

class DataStreamChannelSpec() extends FunSuite {

  import Utils._

  test("request-response") {
    val results =
      execute(
        List(toRequest("increment", 1, Some(IntWrapper(25))), toRequest("decrement", 4, Some(IntWrapper(1001))))
      )

    assertEquals(results(Some(callId(1))), toResponse(1, IntWrapper(26)))
    assertEquals(results(Some(callId(4))), toResponse(4, IntWrapper(1000)))
  }
  test("failure recovery") {
    val results =
      execute(
        List(
          toRequest("increment", 1, Some(IntWrapper(25))),
          toRequest("failure", 17, Some(IntWrapper(42))),
          toRequest("decrement", 4, Some(IntWrapper(1001)))
        ),
        endpoint = Seq(increment, decrement, alwaysFail)
      )

    assertEquals(results(Some(callId(1))), toResponse(1, IntWrapper(26)))

    assertEquals(results(Some(callId(4))), toResponse(4, IntWrapper(1000)))

    assertEquals(results(Some(callId(17))), toError(17, "oh no:("))
  }
}

private object Utils {

  def callId(i: Long) = CallId.NumberId(i)

  case class IntWrapper(value: Int)
  object IntWrapper {
    implicit val jcodec: JsonValueCodec[IntWrapper] = JsonCodecMaker.make
  }

  val increment = Endpoint[Future]("increment").simple { (in: IntWrapper) =>
    Future(in.copy(value = in.value + 1))
  }

  val decrement = Endpoint[Future]("decrement").simple { (in: IntWrapper) =>
    Future(in.copy(value = in.value - 1))
  }

  val alwaysFail = Endpoint[Future]("failure").simple { (in: IntWrapper) =>
    Future.failed[IntWrapper](new Exception("oh no:("))
  }

  def execute(inputs: Seq[String], endpoint: Seq[Endpoint[Future]] = List(increment, decrement)) = {
    val buf = new ByteArrayInputStream(
      inputs
        .map { cont =>
          s"Content-Length: ${cont.getBytes().length}\n\n$cont"
        }
        .mkString
        .getBytes()
    )

    val out = new ByteArrayOutputStream()

    val ch = new StreamChannel(new DataInputStream(buf), new DataOutputStream(out), endpoint.toList)

    try { ch.loop() }
    catch {
      case NonFatal(err) =>
    }
    val bytes = out.toByteArray()
    readBack(new ByteArrayInputStream(bytes), bytes.length).map(rm => rm.id -> rm).toMap
  }

  def readBack(inStream: InputStream, maxLength: Int) = {
    val messages = Vector.newBuilder[RawMessage]
    var keepRunning = true
    val ds = new DataInputStream(inStream)
    while (keepRunning) {
      LSPHeaders.readNext(ds) match {
        case Left(value) =>
          keepRunning = false
        case Right(value) =>
          val array = Array.fill(value.contentLength)(ds.readByte())

          messages += Codec.decode[RawMessage](Some(Payload(array))).fold(throw _, identity)
      }
    }

    messages.result()
  }

  def toRequest[T: JsonValueCodec](method: String, id: Int, params: Option[T]) = {
    import Codec._
    val msg = RawMessage.from(InputMessage.RequestMessage(method, CallId.NumberId(id), params.map(Codec.encode[T])))
    writeToStringReentrant(msg)
  }
  def toResponse[T: JsonValueCodec](id: Int, params: T) = {
    import Codec._
    RawMessage.from(OutputMessage.ResponseMessage(CallId.NumberId(id), Codec.encode(params)))
  }
  def toError(id: Int, msg: String, code: Int = 0) = {
    import Codec._
    RawMessage.from(OutputMessage.ErrorMessage(callId(id), ErrorPayload(code, msg, None)))
  }
}
