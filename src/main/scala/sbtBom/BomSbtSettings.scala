package sbtBom

import java.io.{FileOutputStream, FileWriter, StringWriter, Writer}
import java.nio.channels.Channels
import sbt.*
import sbt.Keys.*
import sbtBom.BomSbtPlugin.autoImport.*
import Defaults.prefix
import org.cyclonedx.CycloneDxSchema.Version
import org.w3c.dom.Document

import java.nio.charset.StandardCharsets
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult
import scala.util.control.Exception.ultimately

object BomSbtSettings {
  def projectSettings: Seq[Setting[_]] =
    inConfig(Compile)(bomSettings) ++
    inConfig(Test)(bomSettings)

  private def bomSettings = Seq(
    bomFileName := "bom.xml",
    targetBomFile := target.value / (prefix(configuration.value.name) + bomFileName.value),
    makeBom := makeBomTask.value,
    listBom := listBomTask.value,
    noCompileDependenciesInOtherReports := true
  )

  private def makeBomTask: Def.Initialize[Task[sbt.File]] = Def.task[File] {
    val log = sLog.value
    val bomFile = targetBomFile.value

    log.info(s"Creating bom file ${bomFile.getAbsolutePath}")

    writeXmlToFile(generateBom.value, bomFile)

    log.info(s"Bom file ${bomFile.getAbsolutePath} created")

    bomFile
  }

  private def listBomTask: Def.Initialize[Task[String]] = Def.task[String] {
    val log = sLog.value
    log.info("Creating bom")

    val bomText =
      writeXmlToText(generateBom.value)

    log.info("Bom created")

    bomText
  }

  private def applyCrossVersion(name: String, crossFunction: Option[String => String]): String = {
    crossFunction match {
      case None         => name
      case Some(cross)  => cross(name)
    }
  }

  private def generateBom = Def.task {
    val report = Classpaths.updateTask.value
    val rootModule = projectID.value

    val crossedVersionRootModule =
      rootModule
        .withName(
          applyCrossVersion(
            rootModule.name,
            CrossVersion(rootModule.crossVersion, scalaVersion.value, scalaBinaryVersion.value)))

    val ignoreModules: Seq[ModuleReport] =
      if (configuration.value != Compile && noCompileDependenciesInOtherReports.value)
        report.configuration(Compile).map(_.modules).getOrElse(Seq.empty)
      else
        Seq.empty

    new BomBuilder(
      crossedVersionRootModule,
      report.configuration(configuration.value),
      ignoreModules,
      Version.VERSION_13
    ).buildXml
  }

  private def writeXmlToFile(xml: Document, destFile: sbt.File): Unit = {
    destFile.getParentFile.mkdirs
    val writer = new FileWriter(destFile, StandardCharsets.UTF_8);
    ultimately(writer.close()) {
      writeXml(xml, writer)
    }
  }

  private def writeXmlToText(xml: Document): String = {
    val writer = new StringWriter();
    writeXml(xml, writer)
    writer.toString
  }

  private def writeXml(xml: Document, writer: Writer): Unit = {
    TransformerFactory.newInstance().newTransformer().transform(new DOMSource(xml), new StreamResult(writer));
  }
}
