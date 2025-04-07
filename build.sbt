import org.typelevel.sbt.tpolecat.DevMode
import org.typelevel.sbt.tpolecat.OptionsMode
import java.net.URI

inThisBuild(
  List(
    organization := "org.polyvariant",
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

ThisBuild / tpolecatOptionsMode := DevMode

val commonSettings = Seq(
  // https://github.com/scala/scala3/issues/18674
  Test / scalacOptions -= "-Wunused:implicits",
  Test / scalacOptions -= "-Wunused:explicits",
  Test / scalacOptions -= "-Wunused:imports",
  Test / scalacOptions -= "-Wunused:locals",
  Test / scalacOptions -= "-Wunused:params",
  Test / scalacOptions -= "-Wunused:privates",
  //
  libraryDependencies ++= Seq(
    "com.disneystreaming" %%% "weaver-cats" % "0.8.4" % Test
  ),
  mimaPreviousArtifacts := Set(
    organization.value %%% name.value % "0.0.7"
  ),
  scalacOptions += "-java-output-version:8"
)

val core = projectMatrix
  .in(file("modules") / "core")
  .jvmPlatform(
    jvmScalaVersions,
    Test / unmanagedSourceDirectories ++= Seq(
      (file("modules") / "core" / "src" / "test" / "scalajvm-native").getAbsoluteFile
    )
  )
  .jsPlatform(jsScalaVersions)
  .nativePlatform(
    nativeScalaVersions,
    Test / unmanagedSourceDirectories ++= Seq(
      (file("modules") / "core" / "src" / "test" / "scalajvm-native").getAbsoluteFile
    )
  )
  .disablePlugins(AssemblyPlugin)
  .settings(
    name := "jsonrpclib-core",
    commonSettings,
    libraryDependencies ++= Seq(
      "com.github.plokhotnyuk.jsoniter-scala" %%% "jsoniter-scala-macros" % "2.17.0"
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
      "co.fs2" %%% "fs2-io" % "3.12.0"
    )
  )

val exampleServer = projectMatrix
  .in(file("modules") / "examples/server")
  .jvmPlatform(List(scala213))
  .dependsOn(fs2)
  .settings(
    commonSettings,
    publish / skip := true
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
    publish / skip := true
  )
  .disablePlugins(MimaPlugin)

val root = project
  .in(file("."))
  .settings(
    publish / skip := true
  )
  .disablePlugins(MimaPlugin, AssemblyPlugin)
  .aggregate(List(core, fs2, exampleServer, exampleClient).flatMap(_.projectRefs): _*)

// need to compile first because of https://github.com/plokhotnyuk/jsoniter-scala/issues/564
addCommandAlias("ci", "compile;test;scalafmtCheckAll;mimaReportBinaryIssues")
