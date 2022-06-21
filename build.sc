import $ivy.`com.lihaoyi::mill-contrib-bloop:$MILL_VERSION`

import os.Path
import mill._
import scalalib._
import scalajslib._
import scalanativelib._

object versions {
  val scala212Version = "2.12.16"
  val scala213Version = "2.13.8"
  val scala3Version = "3.1.2"
  val scalaJSVersion = "1.10.0"
  val scalaNativeVersion = "0.4.4"
  val munitVersion = "0.7.29"

  val crossMap = Map(
    "2.13" -> scala213Version,
    "2.12" -> scala212Version,
    "3" -> scala3Version
  )
}
import versions._

object lib extends Cross[LibModule](versions.crossMap.keys.toSeq: _*)
class LibModule(versionKey: String) extends define.Module {

  val crossVersion = crossMap(versionKey)
  def crossPlatformIvyDeps: T[Agg[Dep]] = Agg(
    ivy"com.github.plokhotnyuk.jsoniter-scala::jsoniter-scala-macros::2.13.31",
    ivy"io.circe::circe-core::0.14.2"
  )

  object jvm extends RpcCrossScalaModule {
    def crossScalaVersion = crossVersion
    def targetPlatform: Platform = Platform.JVM
    def ivyDeps = T(super.ivyDeps() ++ crossPlatformIvyDeps())
    object test extends Tests
  }

  object js extends ScalaJSModule with RpcCrossScalaModule {
    def crossScalaVersion = crossVersion
    def targetPlatform: Platform = Platform.JS
    def scalaJSVersion = versions.scalaJSVersion
    def ivyDeps = T(super.ivyDeps() ++ crossPlatformIvyDeps())
    object test extends Tests
  }

  // TODO : enable when circe publishes against native
  // object native extends ScalaNativeModule with RpcCrossScalaModule {
  //   def crossScalaVersion = crossVersion
  //   def targetPlatform: Platform = Platform.Native
  //   def scalaNativeVersion = "0.4.4"
  //   def ivyDeps = T(super.ivyDeps() ++ crossPlatformIvyDeps())
  //   object test extends Tests
  // }

}

object fs2 extends Cross[FS2Module](versions.crossMap.keys.toSeq: _*)
class FS2Module(versionKey: String) extends define.Module {

  val crossVersion = crossMap(versionKey)
  def crossPlatformIvyDeps: T[Agg[Dep]] = Agg(
    ivy"com.github.plokhotnyuk.jsoniter-scala::jsoniter-scala-macros::2.13.31",
    ivy"io.circe::circe-core::0.14.2"
  )

  object jvm extends RpcCrossScalaModule {
    def moduleDeps = Seq(lib(versionKey).jvm)
    def crossScalaVersion = crossVersion
    def targetPlatform: Platform = Platform.JVM
    def ivyDeps = T(super.ivyDeps() ++ crossPlatformIvyDeps())
    object test extends Tests
  }

  object js extends ScalaJSModule with RpcCrossScalaModule {
    def moduleDeps = Seq(lib(versionKey).js)
    def crossScalaVersion = crossVersion
    def targetPlatform: Platform = Platform.JS
    def scalaJSVersion = versions.scalaJSVersion
    def ivyDeps = T(super.ivyDeps() ++ crossPlatformIvyDeps())
    object test extends Tests
  }
}

sealed abstract class Platform(val value: String)
object Platform {
  case object JVM extends Platform("jvm")
  case object JS extends Platform("js")
  case object Native extends Platform("native")
}

trait RpcCrossScalaModule extends CrossPlatformScalaModule {

  def targetPlatform: Platform

  override def millSourcePath: Path = super.millSourcePath / os.up

  trait Tests extends super.Tests with TestModule.Munit {
    def ivyDeps = Agg(ivy"org.scalameta::munit:$munitVersion")
  }
}

trait CrossPlatformScalaModule extends ScalaModule with CrossModuleBase {
  outer =>

  def targetPlatform: Platform

  private def all(path: os.Path) = {
    Set(PathRef(path / s"src-${targetPlatform.value}")) ++
      scalaVersionDirectoryNames.map { s => PathRef(path / s"src-${targetPlatform.value}-$s") } ++
      scalaVersionDirectoryNames.map(s => PathRef(path / s"src-$s"))
  }

  override def sources = T.sources {
    super.sources() ++ all(millSourcePath)
  }

  trait CrossPlatfomScalaModuleTests extends ScalaModuleTests {
    override def sources = T.sources {
      super.sources() ++ all(millSourcePath)
    }
  }
  trait Tests extends CrossPlatfomScalaModuleTests
}
