package sbtBom

import com.github.packageurl.PackageURL
import org.cyclonedx.{BomGeneratorFactory, CycloneDxSchema}
import org.cyclonedx.CycloneDxSchema.Version
import org.cyclonedx.model.{Bom, Component, License, LicenseChoice}
import org.cyclonedx.util.{BomUtils, LicenseResolver}
import sbt.librarymanagement.{ConfigurationReport, ModuleReport}

import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.sax.SAXResult
import scala.xml.Node
import scala.xml.parsing.NoBindingFactoryAdapter

class BomBuilder(
  reportOption: Option[ConfigurationReport],
  ignoreModules: Seq[ModuleReport],
  schemaVersion: Version
) {
  private def toBom(report: ConfigurationReport): Bom = {
    val bom = new Bom
    //TODO: Add Serial Number for Spec >= 1.1
    //TODO: Add Metadata & Dependencies for Spec >= 1.2
    report.modules.map(toComponent).foreach(bom.addComponent)
    bom
  }

  def buildXml: Node = {
    val adapter = new NoBindingFactoryAdapter
    TransformerFactory
      .newInstance()
      .newTransformer()
      .transform(
        new DOMSource(
          BomGeneratorFactory
            .createXml(schemaVersion, reportOption.map(toBom).getOrElse(new Bom))
            .generate()
        ),
        new SAXResult(adapter)
      )
    adapter.rootElem
  }

  private def toComponent(moduleReport: ModuleReport): Component = {
    val component = new Component
    component.setGroup(moduleReport.module.organization)
    component.setName(moduleReport.module.name)
    component.setVersion(moduleReport.module.revision)
    component.setLicenseChoice(toLicenseChoice(moduleReport.licenses))
    component.setType(Component.Type.LIBRARY)
    component.setPurl(
      new PackageURL(
        PackageURL.StandardTypes.MAVEN,
        component.getGroup,
        component.getName,
        component.getVersion,
        null,
        null))
    moduleReport.artifacts
      .find(artifact => artifact._1.`type`.equalsIgnoreCase("jar"))
      .map(artefact => BomUtils.calculateHashes(artefact._2, schemaVersion))
      .foreach(component.setHashes)
    component.setModified(false)
    component.setBomRef(component.getPurl)
    component
  }

  private def resolveLicense(licenseString: String, licenseChoice: LicenseChoice): Boolean = {
    val resolvedLicenses = LicenseResolver.resolve(licenseString)
    if (resolvedLicenses != null) {
      if (resolvedLicenses.getLicenses != null && !resolvedLicenses.getLicenses.isEmpty) {
        licenseChoice.addLicense(resolvedLicenses.getLicenses.get(0))
        return true
      }
    }
    false
  }

  private def toLicenseChoice(licenses: Vector[(String, Option[String])]): LicenseChoice = {
    val licenseChoice = new LicenseChoice

    licenses.foreach(license => {
      val licenseName = license._1
      val licenseUrl = license._2
      var resolved = false

      if (licenseName != null) {
        resolved = resolveLicense(licenseName, licenseChoice)
      }

      if (!resolved && licenseUrl != null && licenseUrl.isDefined) {
        resolved = resolveLicense(licenseUrl.get, licenseChoice)
      }

      if (!resolved) {
        val license = new License
        license.setName(licenseName)
        licenseChoice.addLicense(license)
      }
    })

    licenseChoice
  }
}
