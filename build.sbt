import org.typelevel.sbt.tpolecat.DevMode
import org.typelevel.sbt.tpolecat.OptionsMode
import java.net.URI

inThisBuild(
  List(
    organization := "tech.neander",
    homepage := Some(url("https://github.com/neandertech/jsonrpclib")),
    licenses := List(License.Apache2),
    developers := List(
      Developer("Baccata", "Olivier Mélois", "baccata64@gmail.com", URI.create("https://github.com/baccata").toURL)
    ),
    sonatypeCredentialHost := "s01.oss.sonatype.org",
    sonatypeRepository := "https://s01.oss.sonatype.org/service/local"
  )
)

val scala213 = "2.13.16"
val scala3 = "3.3.5"
val allScalaVersions = List(scala213, scala3)
val jvmScalaVersions = allScalaVersions
val jsScalaVersions = allScalaVersions
val nativeScalaVersions = allScalaVersions

val fs2Version = "3.12.0"

ThisBuild / tpolecatOptionsMode := DevMode

val commonSettings = Seq(
  libraryDependencies ++= Seq(
    "com.disneystreaming" %%% "weaver-cats" % "0.8.4" % Test
  ),
  mimaPreviousArtifacts := Set(
    organization.value %%% name.value % "0.0.7"
  ),
  scalacOptions += "-java-output-version:11"
)

val core = projectMatrix
  .in(file("modules") / "core")
  .jvmPlatform(
    jvmScalaVersions,
    Test / unmanagedSourceDirectories ++= Seq(
      (projectMatrixBaseDirectory.value / "src" / "test" / "scalajvm-native").getAbsoluteFile
    )
  )
  .jsPlatform(jsScalaVersions)
  .nativePlatform(
    nativeScalaVersions,
    Test / unmanagedSourceDirectories ++= Seq(
      (projectMatrixBaseDirectory.value / "src" / "test" / "scalajvm-native").getAbsoluteFile
    )
  )
  .disablePlugins(AssemblyPlugin)
  .settings(
    name := "jsonrpclib-core",
    commonSettings,
    libraryDependencies ++= Seq(
      "com.github.plokhotnyuk.jsoniter-scala" %%% "jsoniter-scala-macros" % "2.30.2"
    )
  )

val fs2 = projectMatrix
  .in(file("modules") / "fs2")
  .jvmPlatform(jvmScalaVersions)
  .jsPlatform(jsScalaVersions)
  .nativePlatform(nativeScalaVersions)
  .disablePlugins(AssemblyPlugin)
  .dependsOn(core)
  .settings(
    name := "jsonrpclib-fs2",
    commonSettings,
    libraryDependencies ++= Seq(
      "co.fs2" %%% "fs2-core" % fs2Version
    )
  )

val smithy = projectMatrix
  .in(file("modules") / "smithy")
  // TODO
  // .jvmPlatform(jvmScalaVersions)
  // .jsPlatform(jsScalaVersions)
  // .nativePlatform(nativeScalaVersions)
  .jvmPlatform(
    autoScalaLibrary = false,
    scalaVersions = Seq.empty,
    settings = Seq()
  )
  .disablePlugins(AssemblyPlugin, MimaPlugin)
  .settings(
    name := "jsonrpclib-smithy"
  )

val smithy4s = projectMatrix
  .in(file("modules") / "smithy4s")
  .jvmPlatform(jvmScalaVersions)
  .jsPlatform(jsScalaVersions)
  .nativePlatform(Seq(scala3))
  .disablePlugins(AssemblyPlugin)
  .enablePlugins(Smithy4sCodegenPlugin)
  .dependsOn(fs2)
  .dependsOn(smithy.projectRefs.head)
  .settings(
    name := "jsonrpclib-smithy4s",
    commonSettings,
    mimaPreviousArtifacts := Set.empty,
    libraryDependencies ++= Seq(
      "co.fs2" %%% "fs2-core" % fs2Version,
      "com.disneystreaming.smithy4s" %%% "smithy4s-json" % smithy4sVersion.value
    )
  )

val exampleServer = projectMatrix
  .in(file("modules") / "examples/server")
  .jvmPlatform(List(scala213))
  .dependsOn(fs2)
  .settings(
    commonSettings,
    publish / skip := true,
    libraryDependencies ++= Seq(
      "co.fs2" %%% "fs2-io" % fs2Version
    )
  )
  .disablePlugins(MimaPlugin)

val exampleClient = projectMatrix
  .in(file("modules") / "examples/client")
  .jvmPlatform(
    List(scala213),
    Seq(
      fork := true,
      envVars += "SERVER_JAR" -> (exampleServer.jvm(scala213) / assembly).value.toString
    )
  )
  .disablePlugins(AssemblyPlugin)
  .dependsOn(fs2)
  .settings(
    commonSettings,
    publish / skip := true,
    libraryDependencies ++= Seq(
      "co.fs2" %%% "fs2-io" % fs2Version
    )
  )
  .disablePlugins(MimaPlugin)

val exampleSmithyShared = projectMatrix
  .in(file("modules") / "examples/smithyShared")
  .jvmPlatform(List(scala213))
  .dependsOn(smithy4s)
  .enablePlugins(Smithy4sCodegenPlugin)
  .settings(
    commonSettings,
    publish / skip := true
  )
  .disablePlugins(MimaPlugin)

val exampleSmithyServer = projectMatrix
  .in(file("modules") / "examples/smithyServer")
  .jvmPlatform(List(scala213))
  .dependsOn(exampleSmithyShared)
  .settings(
    commonSettings,
    publish / skip := true,
    libraryDependencies ++= Seq(
      "co.fs2" %%% "fs2-io" % fs2Version
    ),
    assembly / assemblyMergeStrategy := {
      case PathList("META-INF", "smithy", _*)           => MergeStrategy.concat
      case PathList("jsonrpclib", "package.class")      => MergeStrategy.first
      case PathList("META-INF", xs @ _*) if xs.nonEmpty => MergeStrategy.discard
      case x                                            => MergeStrategy.first
    }
  )
  .disablePlugins(MimaPlugin)

val exampleSmithyClient = projectMatrix
  .in(file("modules") / "examples/smithyClient")
  .jvmPlatform(
    List(scala213),
    Seq(
      fork := true,
      envVars += "SERVER_JAR" -> (exampleSmithyServer.jvm(scala213) / assembly).value.toString
    )
  )
  .dependsOn(exampleSmithyShared)
  .settings(
    commonSettings,
    publish / skip := true,
    libraryDependencies ++= Seq(
      "co.fs2" %%% "fs2-io" % fs2Version
    )
  )
  .disablePlugins(MimaPlugin, AssemblyPlugin)

val root = project
  .in(file("."))
  .settings(
    publish / skip := true
  )
  .disablePlugins(MimaPlugin, AssemblyPlugin)
  .aggregate(
    List(
      core,
      fs2,
      exampleServer,
      exampleClient,
      smithy,
      smithy4s,
      exampleSmithyShared,
      exampleSmithyServer,
      exampleSmithyClient
    ).flatMap(_.projectRefs): _*
  )

// The core compiles are a workaround for https://github.com/plokhotnyuk/jsoniter-scala/issues/564
// when we switch to SN 0.5, we can use `makeWithSkipNestedOptionValues` instead: https://github.com/plokhotnyuk/jsoniter-scala/issues/564#issuecomment-2787096068
val compileCoreModules = {
  for {
    scalaVersionSuffix <- List("", "3")
    platformSuffix <- List("", "JS", "Native")
    task <- List("compile", "package")
  } yield s"core$platformSuffix$scalaVersionSuffix/$task"
}.mkString(";")

addCommandAlias(
  "ci",
  s"$compileCoreModules;test;scalafmtCheckAll;mimaReportBinaryIssues"
)
