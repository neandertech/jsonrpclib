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
val jdkVersion = 11
val allScalaVersions = List(scala213, scala3)
val jvmScalaVersions = allScalaVersions
val jsScalaVersions = allScalaVersions
val nativeScalaVersions = allScalaVersions

val fs2Version = "3.12.0"

ThisBuild / versionScheme := Some("early-semver")
ThisBuild / tpolecatOptionsMode := DevMode

val commonSettings = Seq(
  libraryDependencies ++= Seq(
    "com.disneystreaming" %%% "weaver-cats" % "0.8.4" % Test
  ),
  mimaPreviousArtifacts := Set(
    // organization.value %%% name.value % "0.0.7"
  ),
  scalacOptions ++= {
    CrossVersion.partialVersion(scalaVersion.value) match {
      case Some((2, _)) => Seq(s"-target:jvm-$jdkVersion")
      case _            => Seq(s"-java-output-version:$jdkVersion")
    }
  }
)

val commonJvmSettings = Seq(
  javacOptions ++= Seq("--release", jdkVersion.toString)
)

val core = projectMatrix
  .in(file("modules") / "core")
  .jvmPlatform(
    jvmScalaVersions,
    Seq(
      Test / unmanagedSourceDirectories ++= Seq(
        (projectMatrixBaseDirectory.value / "src" / "test" / "scalajvm-native").getAbsoluteFile
      )
    ) ++ commonJvmSettings
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
      "com.github.plokhotnyuk.jsoniter-scala" %%% "jsoniter-scala-circe" % "2.30.2"
    )
  )

val fs2 = projectMatrix
  .in(file("modules") / "fs2")
  .jvmPlatform(jvmScalaVersions, commonJvmSettings)
  .jsPlatform(jsScalaVersions)
  .nativePlatform(nativeScalaVersions)
  .disablePlugins(AssemblyPlugin)
  .dependsOn(core)
  .settings(
    name := "jsonrpclib-fs2",
    commonSettings,
    libraryDependencies ++= Seq(
      "co.fs2" %%% "fs2-core" % fs2Version,
      "io.circe" %%% "circe-generic" % "0.14.7" % Test
    )
  )

val smithy = projectMatrix
  .in(file("modules") / "smithy")
  .jvmPlatform(false)
  .disablePlugins(AssemblyPlugin, MimaPlugin)
  .enablePlugins(SmithyTraitCodegenPlugin)
  .settings(
    name := "jsonrpclib-smithy",
    commonJvmSettings,
    smithyTraitCodegenDependencies := List(Dependencies.alloy.core),
    smithyTraitCodegenJavaPackage := "jsonrpclib",
    smithyTraitCodegenNamespace := "jsonrpclib"
  )

lazy val buildTimeProtocolDependency =
  /** By default, smithy4sInternalDependenciesAsJars doesn't contain the jars in the "smithy4s" configuration. We have
    * to add them manually - this is the equivalent of a "% Smithy4s"-scoped dependency.
    *
    * Ideally, this would be
    * {{{
    * (Compile / smithy4sInternalDependenciesAsJars) ++=
    *   Smithy4s / smithy4sInternalDependenciesAsJars).value.map(_.data)
    * }}}
    *
    * but that doesn't work because the Smithy4s configuration doesn't extend from Compile so it doesn't have the
    * `internalDependencyAsJars` setting.
    */
  Compile / smithy4sInternalDependenciesAsJars ++=
    (smithy.jvm(autoScalaLibrary = false) / Compile / fullClasspathAsJars).value.map(_.data)

val smithy4s = projectMatrix
  .in(file("modules") / "smithy4s")
  .jvmPlatform(jvmScalaVersions, commonJvmSettings)
  .jsPlatform(jsScalaVersions)
  .nativePlatform(Seq(scala3))
  .disablePlugins(AssemblyPlugin)
  .enablePlugins(Smithy4sCodegenPlugin)
  .dependsOn(core)
  .settings(
    name := "jsonrpclib-smithy4s",
    commonSettings,
    mimaPreviousArtifacts := Set.empty,
    libraryDependencies ++= Seq(
      "com.disneystreaming.smithy4s" %%% "smithy4s-json" % smithy4sVersion.value
    ),
    buildTimeProtocolDependency
  )

val exampleServer = projectMatrix
  .in(file("modules") / "examples/server")
  .jvmPlatform(List(scala213), commonJvmSettings)
  .dependsOn(fs2)
  .settings(
    commonSettings,
    publish / skip := true,
    libraryDependencies ++= Seq(
      "co.fs2" %%% "fs2-io" % fs2Version,
      "io.circe" %%% "circe-generic" % "0.14.7"
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
    ) ++ commonJvmSettings
  )
  .disablePlugins(AssemblyPlugin)
  .dependsOn(fs2)
  .settings(
    commonSettings,
    publish / skip := true,
    libraryDependencies ++= Seq(
      "co.fs2" %%% "fs2-io" % fs2Version,
      "io.circe" %%% "circe-generic" % "0.14.7"
    )
  )
  .disablePlugins(MimaPlugin)

val exampleSmithyShared = projectMatrix
  .in(file("modules") / "examples/smithyShared")
  .jvmPlatform(List(scala213), commonJvmSettings)
  .dependsOn(smithy4s, fs2)
  .enablePlugins(Smithy4sCodegenPlugin)
  .settings(
    commonSettings,
    publish / skip := true,
    buildTimeProtocolDependency
  )
  .disablePlugins(MimaPlugin)

val exampleSmithyServer = projectMatrix
  .in(file("modules") / "examples/smithyServer")
  .jvmPlatform(List(scala213), commonJvmSettings)
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
    ) ++ commonJvmSettings
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

addCommandAlias(
  "ci",
  s"compile;test;scalafmtCheckAll;mimaReportBinaryIssues"
)
