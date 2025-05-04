import mill.define.Target
import mill.util.Jvm
import $ivy.`com.lihaoyi::mill-contrib-bloop:$MILL_VERSION`
import $ivy.`io.github.davidgregory084::mill-tpolecat::0.3.5`
import $ivy.`io.chris-kipp::mill-ci-release::0.1.9`

import os.Path
import mill._
import mill.scalalib._
import mill.scalalib.publish._
import mill.scalajslib._
import mill.scalanativelib._
import mill.scalajslib.api._
import io.github.davidgregory084._
import io.kipp.mill.ci.release.CiReleaseModule

object versions {
  val scala212Version = "2.12.1"
  val scala213Version = "2.13.11"
  val scala3Version = "3.3.3"
  val scalaJSVersion = "1.14.0"
  val scalaNativeVersion = "0.4.17"
  val munitVersion = "1.0.0-M9"
  val fs2Version = "3.10.0"
  val weaverVersion = "0.8.3"
  val jsoniterVersion = "2.17.0"

  val scala213 = "2.13"
  val scala212 = "2.12"
  val scala3 = "3"

  val crossMap = Map(
    "2.13" -> scala213Version,
    "2.12" -> scala212Version,
    "3" -> scala3Version
  )
}
import versions._

object core extends Module {
  object jvm extends mill.Cross[JvmModule](scala213Version, scala3Version)
  trait JvmModule extends CommonJvmModule {
    def ivyDeps = {
      Agg(
        ivy"com.github.plokhotnyuk.jsoniter-scala::jsoniter-scala-macros::${jsoniterVersion}"
      )
    }

    object test extends MunitTests
  }

  object js extends mill.Cross[JsModule](scala213Version, scala3Version)
  trait JsModule extends CommonJSModule {
    def ivyDeps = {
      Agg(
        ivy"com.github.plokhotnyuk.jsoniter-scala::jsoniter-scala-macros::${jsoniterVersion}"
      )
    }
    object test extends MunitTests
  }
  object native extends mill.Cross[NativeModule](scala213Version, scala3Version)
  trait NativeModule extends CommonNativeModule {
    def ivyDeps = {
      Agg(
        ivy"com.github.plokhotnyuk.jsoniter-scala::jsoniter-scala-macros::${jsoniterVersion}"
      )
    }
    object test extends MunitTests
  }
}

object fs2 extends Module {

  object jvm extends mill.Cross[JvmModule](Seq(scala213Version, scala3Version)) {}
  trait JvmModule extends CommonJvmModule {
    def ivyDeps = {
      Agg(
        ivy"co.fs2::fs2-core::${fs2Version}"
      )
    }
    def moduleDeps = Seq(core.jvm())

    object test extends WeaverTests {}
  }

  object js extends mill.Cross[JSModule](Seq(scala213Version, scala3Version)) {}
  trait JSModule extends CommonJSModule {
    def ivyDeps = {
      Agg(
        ivy"co.fs2::fs2-core::${fs2Version}"
      )
    }
    def moduleDeps = Seq(core.js())

    object test extends WeaverTests {}
  }

  object native extends mill.Cross[NativeModule](Seq(scala213Version, scala3Version)) {}
  trait NativeModule extends CommonNativeModule {
    def ivyDeps = {
      Agg(
        ivy"co.fs2::fs2-core::${fs2Version}"
      )
    }
    def moduleDeps = Seq(core.native())

    object test extends WeaverTests {}
  }
}

object examples extends Module {

  object server extends ScalaModule {
    def ivyDeps = Agg(ivy"co.fs2::fs2-io:${fs2Version}")
    def moduleDeps = Seq(fs2.jvm(scala213Version))
    def scalaVersion = versions.scala213Version
  }

  object client extends ScalaModule {
    def ivyDeps = Agg(ivy"co.fs2::fs2-io:$fs2Version")
    def moduleDeps = Seq(fs2.jvm(scala213Version))
    def scalaVersion = versions.scala213Version
    def forkEnv: Target[Map[String, String]] = T {
      val assembledServer = server.assembly()
      super.forkEnv() ++ Map("SERVER_JAR" -> assembledServer.path.toString())
    }
  }
}

// COMMON SETUP

trait CommonPlatformModule extends JsonRPCModule with PlatformScalaModule with mill.contrib.bloop.Bloop.Module {
  def sources = T.sources {
    super.sources() ++
      (platformScalaSuffix match {
        case "jvm"    => Seq(PathRef(millSourcePath / "src-jvm-native"))
        case "native" => Seq(PathRef(millSourcePath / "src-js-native"), PathRef(millSourcePath / "src-jvm-native"))
        case "js"     => Seq(PathRef(millSourcePath / "src-js-native"))
      })
  }
}

trait CommonTestModule0 extends ScalaModule with mill.contrib.bloop.Bloop.Module

trait CommonJvmModule extends CrossScalaModule with CommonPlatformModule {

  trait CommonTestModule extends ScalaTests with CommonTestModule0

  trait WeaverTests extends CommonTestModule {
    def ivyDeps = super.ivyDeps() ++ Agg(ivy"com.disneystreaming::weaver-cats::$weaverVersion")
    def testFramework = "weaver.framework.CatsEffect"
  }
  trait MunitTests extends CommonTestModule with TestModule.Munit {
    def ivyDeps = super.ivyDeps() ++ Agg(ivy"org.scalameta::munit::$munitVersion")
  }
}

trait CommonJSModule extends ScalaJSModule with CrossScalaModule with CommonPlatformModule {
  override def scalaJSVersion = "1.13.1"
  override def scalacOptions = T {
    super.scalacOptions().filterNot(_ == "-Ywarn-unused:params")
  }

  override def moduleKind = T(ModuleKind.CommonJSModule)
  override def skipIdea = true

  trait CommonTestModule extends ScalaJSTests with CommonTestModule0 {
    override def skipIdea = true
    override def skipBloop = true
  }

  trait WeaverTests extends CommonTestModule {
    def ivyDeps = super.ivyDeps() ++ Agg(ivy"com.disneystreaming::weaver-cats::$weaverVersion")
    def testFramework = "weaver.framework.CatsEffect"
  }
  trait MunitTests extends CommonTestModule with TestModule.Munit {
    def ivyDeps = super.ivyDeps() ++ Agg(ivy"org.scalameta::munit::$munitVersion")
  }
}

trait CommonNativeModule extends ScalaNativeModule with CrossScalaModule with CommonPlatformModule {
  override def scalaNativeVersion = versions.scalaNativeVersion
  override def scalacOptions = T {
    super
      .scalacOptions()
      .filterNot { opts =>
        Seq(
          "-Ywarn-extra-implicit",
          "-Xlint:constant"
        ).contains(opts)
      }
      .filterNot(_.startsWith("-Ywarn-unused"))
  }

  override def skipIdea = true
  override def skipBloop = true

  trait CommonTestModule extends ScalaNativeTests with CommonTestModule0 {
    override def nativeLinkStubs = true
    override def skipIdea = true
    override def skipBloop = true
  }

  trait WeaverTests extends CommonTestModule {
    def ivyDeps = super.ivyDeps() ++ Agg(ivy"com.disneystreaming::weaver-cats::$weaverVersion")
    def testFramework = "weaver.framework.CatsEffect"
  }
  trait MunitTests extends CommonTestModule with TestModule.Munit {
    def ivyDeps = super.ivyDeps() ++ Agg(ivy"org.scalameta::munit::$munitVersion")
  }
}

trait JsonRPCModule extends ScalaModule with CiReleaseModule with scalafmt.ScalafmtModule {
  def scalafmt() = T.command(reformat())
  def fmt() = T.command(reformat())
  def refreshedEnv = T.input(T.ctx().env)
  def publishVersion = T {
    if (refreshedEnv().contains("CI")) super.publishVersion()
    else "dev"
  }
  override def scalacOptions = T {
    super.scalacOptions() ++ Tpolecat.scalacOptionsFor(scalaVersion())
  }

  override def forkEnv = T { refreshedEnv() }

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
}
