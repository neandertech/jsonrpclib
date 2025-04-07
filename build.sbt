import org.typelevel.sbt.tpolecat.DevMode
import org.typelevel.sbt.tpolecat.OptionsMode
val scala213 = "2.13.16"
val scala3 = "3.3.5"
val allScalaVersions = List(scala213, scala3)
val jvmScalaVersions = allScalaVersions
val jsScalaVersions = allScalaVersions
val nativeScalaVersions = allScalaVersions

/*

  def pomSettings = PomSettings(
    description = "A Scala jsonrpc library",
    organization = "tech.neander",
    url = "https://github.com/neandertech/jsonrpclib",
    licenses = Seq(License.`Apache-2.0`),
    versionControl = VersionControl(Some("https://github.com/neandertech/jsonrpclib")),
    developers = Seq(
      Developer("Baccata", "Olivier Mélois", "https://github.com/baccata")
    )
  )

  override def sonatypeUri = "https://s01.oss.sonatype.org/service/local"
  override def sonatypeSnapshotUri =
    "https://s01.oss.sonatype.org/content/repositories/snapshots"
 */

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
  )
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

val root = project
  .in(file("."))
  .settings(
    publish / skip := true
  )
  .aggregate(List(core, fs2, exampleServer, exampleClient).flatMap(_.projectRefs): _*)
