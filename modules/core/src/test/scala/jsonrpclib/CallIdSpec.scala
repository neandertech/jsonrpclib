package jsonrpclib

import weaver._
import com.github.plokhotnyuk.jsoniter_scala.core._
import io.circe.Json
import com.github.plokhotnyuk.jsoniter_scala.circe.JsoniterScalaCodec._
import cats.syntax.all._

object CallIdSpec extends FunSuite {
  test("json parsing") {
    val strJson = """ "25" """.trim

    val intJson = "25"

    val longJson = Long.MaxValue.toString

    val nullJson = "null"
    assert.same(readFromString[Json](strJson).as[CallId], CallId.StringId("25").asRight) &&
    assert.same(readFromString[Json](intJson).as[CallId], CallId.NumberId(25).asRight) &&
    assert.same(readFromString[Json](longJson).as[CallId], CallId.NumberId(Long.MaxValue).asRight) &&
    assert.same(readFromString[Json](nullJson).as[CallId], CallId.NullId.asRight)
  }
}
