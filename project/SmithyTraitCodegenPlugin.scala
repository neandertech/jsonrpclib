import sbt.*
import sbt.plugins.JvmPlugin
import software.amazon.smithy.build.FileManifest
import software.amazon.smithy.build.PluginContext
import software.amazon.smithy.model.node.ArrayNode
import software.amazon.smithy.model.node.ObjectNode
import software.amazon.smithy.model.Model
import software.amazon.smithy.traitcodegen.TraitCodegenPlugin

import Keys.*

object SmithyTraitCodegenPlugin extends AutoPlugin {
  override def trigger: PluginTrigger = noTrigger
  override def requires: Plugins = JvmPlugin

  override def projectSettings: Seq[Setting[?]] =
    Seq(
      Keys.generateSmithyTraits := Def.task {
        import sbt.util.CacheImplicits.*
        val s = (Compile / streams).value
        val logger = sLog.value
        val args = SmithyTraitCodegen.Args(
          targetDir = os.Path((Compile / target).value),
          smithySourcesDir = PathRef((Compile / resourceDirectory).value / "META-INF" / "smithy"),
          dependencies = List.empty
        )
        val cachedCodegen =
          Tracked.inputChanged[SmithyTraitCodegen.Args, SmithyTraitCodegen.Output](
            s.cacheStoreFactory.make("smithy-trait-codegen-args")
          ) {
            Function.untupled(
              Tracked
                .lastOutput[(Boolean, SmithyTraitCodegen.Args), SmithyTraitCodegen.Output](
                  s.cacheStoreFactory.make("smithy-trait-codegen-output")
                ) { case ((inputChanged, codegenArgs), cached) =>
                  cached
                    .filter(_ => !inputChanged)
                    .fold {
                      SmithyTraitCodegen.generate(codegenArgs)
                    } { last =>
                      logger.info(s"Using cached result of smithy-trait-codegen")
                      last
                    }
                }
            )
          }
        cachedCodegen(args)
      }.value,
      Compile / sourceGenerators += Def.task {
        val codegenOutput = (Compile / Keys.generateSmithyTraits).value
        cleanCopy(source = codegenOutput.javaDir, target = (Compile / sourceManaged).value / "java")
      },
      Compile / resourceGenerators += Def.task {
        val codegenOutput = (Compile / Keys.generateSmithyTraits).value
        cleanCopy(source = codegenOutput.metaDir, target = (Compile / resourceManaged).value)
      }.taskValue,
      libraryDependencies += "software.amazon.smithy" % "smithy-model" % "1.56.0"
    )

  private def cleanCopy(source: File, target: File) = {
    val sourcePath = os.Path(source)
    val targetPath = os.Path(target)
    os.remove.all(targetPath)
    os.copy(from = sourcePath, to = targetPath, createFolders = true)
    os.walk(targetPath).map(_.toIO).filter(_.isFile())
  }

  object Keys {
    val generateSmithyTraits =
      taskKey[SmithyTraitCodegen.Output]("Run AWS smithy-trait-codegen on the protocol specs")
  }

}
