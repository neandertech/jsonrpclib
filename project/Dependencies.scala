import sbt.*

object Dependencies {
  val alloy = new {
    val version = "0.3.37"
    val core = "com.disneystreaming.alloy" % "alloy-core" % version
  }
}
