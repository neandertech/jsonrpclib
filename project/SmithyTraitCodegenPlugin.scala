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

  object autoImport {
    val smithyTraitCodegenJavaPackage =
      settingKey[String]("The java target package where the generated smithy traits will be created")
    val smithyTraitCodegenNamespace = settingKey[String]("The smithy namespace where the traits are defined")
    val smithyTraitCodegenDependencies = settingKey[List[ModuleID]]("Dependencies to be added into codegen model")
  }
  import autoImport.*

  override def projectSettings: Seq[Setting[?]] =
    Seq(
      Keys.generateSmithyTraits := Def.task {
        import sbt.util.CacheImplicits.*
        val s = (Compile / streams).value
        val logger = sLog.value

        val report = update.value
        val dependencies = smithyTraitCodegenDependencies.value
        val jars =
          dependencies.flatMap(m =>
            report.matching(moduleFilter(organization = m.organization, name = m.name, revision = m.revision))
          )
        require(
          jars.size == dependencies.size,
          "Not all dependencies required for smithy-trait-codegen have been found"
        )

        val args = SmithyTraitCodegen.Args(
          javaPackage = smithyTraitCodegenJavaPackage.value,
          smithyNamespace = smithyTraitCodegenNamespace.value,
          targetDir = os.Path((Compile / target).value),
          smithySourcesDir = PathRef((Compile / resourceDirectory).value / "META-INF" / "smithy"),
          dependencies = jars.map(PathRef(_)).toList
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
      libraryDependencies ++= smithyTraitCodegenDependencies.value
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
