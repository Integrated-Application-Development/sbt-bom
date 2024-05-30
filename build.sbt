lazy val root = (project in file("."))
  .enablePlugins(ScriptedPlugin)
  .settings(
    name := "sbt-bom",
    organization := "com.integradev.3rdparty",
    organizationName := "SBT BOM",
    version := "1.0.0-SNAPSHOT",
    sbtPlugin := true,
    scalaVersion := "2.12.15",
    libraryDependencies ++= Dependencies.library,
    scriptedLaunchOpts += ("-Dplugin.version=" + version.value),
    scriptedBufferLog := false,
    dependencyOverrides += "org.typelevel" %% "jawn-parser" % "0.14.1",
    publishMavenStyle := false
  )
