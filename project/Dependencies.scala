import sbt._

object Dependencies {
  val jna = "net.java.dev.jna" % "jna" % "4.5.0"
  val sbtIO = "org.scala-sbt" %% "io" % "1.0.1"
  val scalaTest = "org.scalatest" %% "scalatest" % "3.0.4" % Test
}
