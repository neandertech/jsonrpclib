package jsonrpclib.internals

import munit.FunSuite
import java.io.ByteArrayInputStream
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.stream.Collectors
import jsonrpclib.ProtocolError

class HeaderSpec() extends FunSuite {

  test("headers (all)") {
    val result = read(
      "Content-Length: 123\r",
      "Content-type: application/vscode-jsonrpc; charset=utf-8\r",
      "\r",
      "foo..."
    )
    val expected = Result(LSPHeaders(123, "application/vscode-jsonrpc", "utf-8"), "foo...")
    assertEquals(result, Right(expected))
  }

  test("headers (only content-)") {
    val result = read(
      "Content-Length: 123\r",
      "\r",
      "foo..."
    )
    val expected = Result(LSPHeaders(123, "application/json", "UTF-8"), "foo...")
    assertEquals(result, Right(expected))
  }

  test("no header)") {
    val result = read(
      "foo"
    )
    val expected = ProtocolError.ParseError("Could not parse LSP headers")
    assertEquals(result, Left(expected))
  }

  test("missing content-length") {
    val result = read(
      "Content-type: application/vscode-jsonrpc; charset=utf-8\r",
      "\r",
      "foo..."
    )
    val expected = ProtocolError.ParseError("Missing Content-Length header")
    assertEquals(result, Left(expected))
  }

  case class Result(header: LSPHeaders, rest: String)
  def read(lines: String*): Either[ProtocolError.ParseError, Result] = {
    var rest: String = null
    val inputStream = new ByteArrayInputStream(lines.mkString("\n").getBytes());
    try {
      val maybeHeaders = LSPHeaders.readNext(inputStream)
      maybeHeaders.map { headers =>
        // Consuming the rest to check that the header parsing did not read more of the input stream than it should
        val rest = new BufferedReader(new InputStreamReader(inputStream)).lines().collect(Collectors.joining("\n"))
        Result(headers, rest)
      }
    } finally {
      inputStream.close()
    }
  }

}
