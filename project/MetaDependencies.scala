import sbt.*

object MetaDependencies {

  val smithy = new {
    val version = "1.56.0"

    val model = "software.amazon.smithy" % "smithy-model" % version
    val traitCodegen = "software.amazon.smithy" % "smithy-trait-codegen" % version
  }
}
