libraryDependencies ++= Seq(
    "org.cyclonedx" % "cyclonedx-core-java" % "7.3.0"
)

lazy val root = (project in file("."))
  .settings(
    name := "sbt-bom",
    organization := "com.integradev.3rdparty",
    organizationName := "SBT BOM",
    version := "1.1.0",
    sbtPlugin := true,
    scalaVersion := "2.12.19",
    publishMavenStyle := false
  )
