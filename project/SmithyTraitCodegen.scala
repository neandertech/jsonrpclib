import sbt.*
import sbt.io.IO
import software.amazon.smithy.build.FileManifest
import software.amazon.smithy.build.PluginContext
import software.amazon.smithy.model.node.ArrayNode
import software.amazon.smithy.model.node.ObjectNode
import software.amazon.smithy.model.shapes.ShapeId
import software.amazon.smithy.model.Model
import software.amazon.smithy.traitcodegen.TraitCodegenPlugin

import java.io.File
import java.nio.file.Paths
import java.util.UUID

object SmithyTraitCodegen {

  import sjsonnew.*

  import BasicJsonProtocol.*

  case class Args(targetDir: os.Path, smithySourcesDir: PathRef, dependencies: List[PathRef])
  object Args {

    // format: off
    private type ArgsDeconstructed = os.Path :*: PathRef :*: List[PathRef] :*: LNil
    // format: on

    private implicit val pathFormat: JsonFormat[os.Path] =
      BasicJsonProtocol.projectFormat[os.Path, File](p => p.toIO, file => os.Path(file))

    implicit val argsIso =
      LList.iso[Args, ArgsDeconstructed](
        { args: Args =>
          ("targetDir", args.targetDir) :*:
            ("smithySourcesDir", args.smithySourcesDir) :*:
            ("dependencies", args.dependencies) :*:
            LNil
        },
        {
          case (_, targetDir) :*:
              (_, smithySourcesDir) :*:
              (_, dependencies) :*:
              LNil =>
            Args(
              targetDir = targetDir,
              smithySourcesDir = smithySourcesDir,
              dependencies = dependencies
            )
        }
      )

  }

  case class Output(metaDir: File, javaDir: File)

  object Output {

      // format: off
      private type OutputDeconstructed = File :*: File :*: LNil
      // format: on

    implicit val outputIso =
      LList.iso[Output, OutputDeconstructed](
        { output: Output =>
          ("metaDir", output.metaDir) :*:
            ("javaDir", output.javaDir) :*:
            LNil
        },
        {
          case (_, metaDir) :*:
              (_, javaDir) :*:
              LNil =>
            Output(
              metaDir = metaDir,
              javaDir = javaDir
            )
        }
      )
  }

  def generate(args: Args): Output = {
    val outputDir = args.targetDir / "smithy-trait-generator-output"
    val genDir = outputDir / "java"
    val metaDir = outputDir / "meta"
    os.remove.all(outputDir)
    List(outputDir, genDir, metaDir).foreach(os.makeDir.all(_))

    val manifest = FileManifest.create(genDir.toNIO)

    val model = args.dependencies
      .foldLeft(Model.assembler().addImport(args.smithySourcesDir.path.toNIO)) { case (acc, dep) =>
        acc.addImport(dep.path.toNIO)
      }
      .assemble()
      .unwrap()
    val context = PluginContext
      .builder()
      .model(model)
      .fileManifest(manifest)
      .settings(
        ObjectNode
          .builder()
          .withMember("package", "jsonrpclib")
          .withMember("namespace", "jsonrpclib")
          .withMember("header", ArrayNode.builder.build())
          .withMember("excludeTags", ArrayNode.builder.withValue("nocodegen").build())
          .build()
      )
      .build()
    val plugin = new TraitCodegenPlugin()
    plugin.execute(context)
    os.move(genDir / "META-INF", metaDir / "META-INF")
    Output(metaDir = metaDir.toIO, javaDir = genDir.toIO)
  }
}
