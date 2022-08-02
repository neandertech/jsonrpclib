import $ivy.`com.lihaoyi::mill-contrib-bloop:$MILL_VERSION`
import $ivy.`io.github.davidgregory084::mill-tpolecat::0.3.0`

import os.Path
import mill._
import scalalib._
import scalajslib._
import scalanativelib._
import mill.scalajslib.api._
import io.github.davidgregory084.TpolecatModule

object versions {
  val scala212Version = "2.12.16"
  val scala213Version = "2.13.8"
  val scala3Version = "3.1.2"
  val scalaJSVersion = "1.10.0"
  val scalaNativeVersion = "0.4.4"
  val munitVersion = "0.7.29"

  val scala213 = "2.13"
  val scala212 = "2.12"
  val scala3 = "scala3"

  val crossMap = Map(
    "2.13" -> scala213Version,
    "2.12" -> scala212Version,
    "3" -> scala3Version
  )
}
import versions._

object core extends RPCCrossPlatformModule { cross =>

  def crossPlatformIvyDeps: T[Agg[Dep]] = Agg(
    ivy"com.github.plokhotnyuk.jsoniter-scala::jsoniter-scala-macros::2.13.31"
  )

  object jvm extends mill.Cross[JvmModule](scala213, scala3)
  class JvmModule(cv: String) extends cross.JVM(cv) {
    object test extends MunitTests
  }

  object js extends mill.Cross[JsModule](scala213, scala3)
  class JsModule(cv: String) extends cross.JS(cv) {
    object test extends MunitTests
  }

  object native extends mill.Cross[NativeModule](scala213, scala3)
  class NativeModule(cv: String) extends cross.Native(cv) {
    object test extends MunitTests
  }
}

object fs2 extends RPCCrossPlatformModule { cross =>

  override def crossPlatformModuleDeps = Seq(core)
  def crossPlatformIvyDeps: T[Agg[Dep]] = Agg(
    ivy"co.fs2::fs2-core::3.2.8"
  )

  object jvm extends mill.Cross[JvmModule](scala213, scala3)
  class JvmModule(cv: String) extends cross.JVM(cv) {
    object test extends WeaverTests
  }

  object js extends mill.Cross[JsModule](scala213, scala3)
  class JsModule(cv: String) extends cross.JS(cv) {
    object test extends WeaverTests
  }

}

// #############################################################################
//  COMMON SETUP
// #############################################################################

trait RPCCrossPlatformModule extends Module { shared =>

  def artifactName = s"jsonrpclib-${millModuleSegments.parts.mkString("-")}"
  def crossPlatformIvyDeps = T { Agg.empty[Dep] }
  def crossPlatformModuleDeps: Seq[Module] = Seq()
  def crossPlatformTestModuleDeps: Seq[Module] = Seq()

  class JVM(val crossVersion: String) extends PlatformSpecific {
    override def platformLabel: String = "jvm"

    trait WeaverTests extends Tests {
      def ivyDeps = super.ivyDeps() ++ Agg(ivy"com.disneystreaming::weaver-cats:0.7.13")
      def testFramework = "weaver.framework.CatsEffect"
    }

    trait MunitTests extends Tests with TestModule.Munit {
      def ivyDeps = super.ivyDeps() ++ Agg(ivy"org.scalameta::munit::$munitVersion")
    }

    trait Tests extends super.Tests {
      override def moduleDeps = super.moduleDeps ++ shared.crossPlatformTestModuleDeps.flatMap(matchingCross)
    }
  }

  class JS(val crossVersion: String) extends PlatformSpecific with ScalaJSModule {
    override def platformLabel: String = "js"
    override def scalaJSVersion = versions.scalaJSVersion

    override def scalacOptions = T {
      super.scalacOptions().filterNot(_ == "-Ywarn-unused:params")
    }

    override def moduleKind = T(ModuleKind.CommonJSModule)
    override def skipIdea = true

    trait WeaverTests extends Tests {
      def ivyDeps = super.ivyDeps() ++ Agg(ivy"com.disneystreaming::weaver-cats::0.7.13")
      def testFramework = "weaver.framework.CatsEffect"
    }

    trait MunitTests extends Tests with TestModule.Munit {
      def ivyDeps = super.ivyDeps() ++ Agg(ivy"org.scalameta::munit::$munitVersion")
    }

    trait Tests extends super.Tests with mill.contrib.Bloop.Module {
      override def skipIdea = true
      override def skipBloop = true
      override def moduleDeps = super.moduleDeps ++ shared.crossPlatformTestModuleDeps.flatMap(matchingCross).collect {
        case m: ScalaJSModule => m
      }
    }
  }

  class Native(val crossVersion: String) extends PlatformSpecific with ScalaNativeModule {
    override def platformLabel: String = "native"
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

    trait MunitTests extends Tests with TestModule.Munit {
      def ivyDeps = super.ivyDeps() ++ Agg(ivy"org.scalameta::munit::$munitVersion")
    }

    trait Tests extends super.Tests with mill.contrib.Bloop.Module {
      override def nativeLinkStubs = true
      override def skipIdea = true
      override def skipBloop = true
      override def moduleDeps = super.moduleDeps ++ shared.crossPlatformTestModuleDeps.flatMap(matchingCross).collect {
        case m: ScalaNativeModule => m
      }
    }
  }

  trait PlatformSpecific extends JsonRPCModule with mill.contrib.Bloop.Module { self =>
    def platformLabel: String
    def crossVersion: String
    override def scalaVersion = versions.crossMap(crossVersion)

    override def millSourcePath = shared.millSourcePath

    override def ivyDeps = super.ivyDeps() ++ shared.crossPlatformIvyDeps()

    def samePlatform(module: Module): Boolean =
      self match {
        case _: ScalaJSModule     => module.isInstanceOf[ScalaJSModule]
        case _: ScalaNativeModule => module.isInstanceOf[ScalaNativeModule]
        case _ =>
          !(module.isInstanceOf[ScalaJSModule] || module
            .isInstanceOf[ScalaNativeModule])
      }

    def sameScalaVersion(module: Module): Boolean = {
      // Don't know why, pattern matching didn't seem to work here
      module.isInstanceOf[PlatformSpecific] && (module.asInstanceOf[PlatformSpecific].crossVersion == self.crossVersion)
    }

    def sameCross(module: Module) = samePlatform(module) && sameScalaVersion(module)

    def matchingCross(module: Module): Seq[JsonRPCModule] = module match {
      case m: RPCCrossPlatformModule =>
        m.millModuleDirectChildren.collect {
          case cross: Cross[_] =>
            cross.millModuleDirectChildren.collect {
              case child: JsonRPCModule if sameCross(child) => child
            }
          case child: JsonRPCModule if sameCross(child) => Seq(child)
        }.flatten
      case _ => Seq()
    }

    override def moduleDeps: Seq[JsonRPCModule] =
      shared.crossPlatformModuleDeps.flatMap(matchingCross)

    override def artifactName = shared.artifactName

    override def sources = self match {
      case _: ScalaJSModule =>
        T.sources(
          millSourcePath / 'src,
          millSourcePath / s"src-js",
          millSourcePath / s"src-jvm-js"
        )
      case _: ScalaNativeModule =>
        T.sources(
          millSourcePath / 'src,
          millSourcePath / s"src-jvm-native",
          millSourcePath / s"src-native"
        )
      case _ =>
        T.sources(
          millSourcePath / 'src,
          millSourcePath / s"src-jvm",
          millSourcePath / s"src-jvm-js",
          millSourcePath / s"src-jvm-native"
        )
    }

    override def skipBloop = {
      self match {
        case _: ScalaJSModule     => true
        case _: ScalaNativeModule => true
        case _                    => false
      }
    } && { crossVersion != scala213 }

  }
}

trait JsonRPCModule extends ScalaModule with TpolecatModule with scalafmt.ScalafmtModule {
  def scalafmt() = T.command(reformat())
  def fmt() = T.command(reformat())
  def refreshedEnv = T.input(T.ctx().env)
  override def forkEnv = T { refreshedEnv() }
}
