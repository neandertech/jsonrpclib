package jsonrpclib

import weaver._
import com.github.plokhotnyuk.jsoniter_scala.core._

object CallIdSpec extends FunSuite {
  test("json parsing") {
    val strJson = """ "25" """.trim

    val intJson = "25"

    val longJson = Long.MaxValue.toString

    val nullJson = "null"
    assert.same(readFromString[CallId](strJson), CallId.StringId("25")) &&
    assert.same(readFromString[CallId](intJson), CallId.NumberId(25)) &&
    assert.same(readFromString[CallId](longJson), CallId.NumberId(Long.MaxValue)) &&
    assert.same(readFromString[CallId](nullJson), CallId.NullId)
  }
}
