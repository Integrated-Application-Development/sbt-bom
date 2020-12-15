package sbtBom

import sbt._

trait BomSbtKeys {

  lazy val bomFileName = settingKey[String]("Base name of generated bom files")

  lazy val targetBomFile = taskKey[File]("target file to store the generated bom")

  lazy val makeBom = taskKey[File]("Generates bom file which includes all project dependencies")

  lazy val listBom = taskKey[String]("Returns a bom which includes all project dependencies")

  lazy val noCompileDependenciesInOtherReports = settingKey[Boolean]("Reports not of the Compile configuration will not contain Compile dependencies")

}
