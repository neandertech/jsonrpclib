package jsonrpclib
package internals

import munit.FunSuite
import java.io.ByteArrayInputStream
import com.github.plokhotnyuk.jsoniter_scala.core._
import com.github.plokhotnyuk.jsoniter_scala.macros.JsonCodecMaker
import scala.concurrent.Future

import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.InputStream

import scala.concurrent.ExecutionContext.Implicits.global

class JavaIOChannelSpec() extends FunSuite {

  import Utils._

  test("request-response") {
    execute(
      List(toRequest("increment", 1, Some(IntWrapper(25))), toRequest("decrement", 4, Some(IntWrapper(1001))))
    ).map { results =>
      assertEquals(results(Some(callId(1))), toResponse(1, IntWrapper(26)))
      assertEquals(results(Some(callId(4))), toResponse(4, IntWrapper(1000)))
    }
  }

  test("failure recovery") {
    execute(
      List(
        toRequest("increment", 1, Some(IntWrapper(25))),
        toRequest("failure", 17, Some(IntWrapper(42))),
        toRequest("decrement", 4, Some(IntWrapper(1001)))
      ),
      endpoint = Seq(increment, decrement, alwaysFail)
    ).map { results =>
      assertEquals(results(Some(callId(1))), toResponse(1, IntWrapper(26)))

      assertEquals(results(Some(callId(4))), toResponse(4, IntWrapper(1000)))

      assertEquals(results(Some(callId(17))), toError(17, "oh no:("))
    }
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

  val alwaysFail = Endpoint[Future]("failure").simple { (_: IntWrapper) =>
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

    val ch = new JavaIOChannel(new DataInputStream(buf), new DataOutputStream(out), endpoint.toList)

    ch.start().map { _ =>
      val bytes = out.toByteArray()
      readBack(new ByteArrayInputStream(bytes)).map(rm => rm.id -> rm).toMap
    }
  }

  def readBack(inStream: InputStream) = {
    val messages = Vector.newBuilder[RawMessage]
    var keepRunning = true
    val ds = new DataInputStream(inStream)
    while (keepRunning) {
      LSPHeaders.readNext(ds) match {
        case Left(_) =>
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
    val msg =
      RawMessage.from(InputMessage.RequestMessage(method, CallId.NumberId(id.toLong), params.map(Codec.encode[T])))
    writeToStringReentrant(msg)
  }
  def toResponse[T: JsonValueCodec](id: Int, params: T) = {
    import Codec._
    RawMessage.from(OutputMessage.ResponseMessage(CallId.NumberId(id.toLong), Codec.encode(params)))
  }
  def toError(id: Int, msg: String, code: Int = 0) = {
    RawMessage.from(OutputMessage.ErrorMessage(callId(id.toLong), ErrorPayload(code, msg, None)))
  }
}
