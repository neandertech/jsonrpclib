package jsonrpclib

import munit._
import com.github.plokhotnyuk.jsoniter_scala.core._

class CallIdSpec() extends FunSuite {
  test("json parsing") {
    val strJson = """ "25" """.trim
    assertEquals(readFromString[CallId](strJson), CallId.StringId("25"))

    val intJson = "25"
    assertEquals(readFromString[CallId](intJson), CallId.NumberId(25))

    val longJson = Long.MaxValue.toString
    assertEquals(readFromString[CallId](longJson), CallId.NumberId(Long.MaxValue))

    val nullJson = "null"
    assertEquals(readFromString[CallId](nullJson), CallId.NullId)
  }
}
