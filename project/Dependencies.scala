import sbt._

object Dependencies {
  lazy val library = Seq(
    "org.cyclonedx" % "cyclonedx-core-java" % "3.0.7",
    "org.scalatest" %% "scalatest" % "3.0.5" % Test,
  )
}
