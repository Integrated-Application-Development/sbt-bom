package sbtBom

import com.github.packageurl.PackageURL
import org.cyclonedx.CycloneDxSchema.Version
import org.cyclonedx.BomGeneratorFactory
import org.cyclonedx.model.{Bom, Component, Dependency, License, LicenseChoice, Metadata}
import org.cyclonedx.util.{BomUtils, LicenseResolver}
import sbt.librarymanagement.{ConfigurationReport, ModuleID, ModuleReport}

import java.time.LocalDate
import java.util.{Date, UUID}

class BomBuilder(
  project: ModuleID,
  reportOption: Option[ConfigurationReport],
  ignoreModules: Seq[ModuleReport],
  schemaVersion: Version
) {
  private class ModuleDetails(
    val component: Component,
    val dependency: Dependency,
    val callers: Vector[ModuleID]
  ) {}

  private object ModuleDetails {
    def moduleDetails(moduleReport: ModuleReport): ModuleDetails = {
      val component = toComponent(moduleReport);
      new ModuleDetails(
        component,
        new Dependency(component.getPurl),
        moduleReport.callers.map(_.caller))
    }
  }

  def buildXml = {
    BomGeneratorFactory
        .createXml(schemaVersion, reportOption.map(toBom).getOrElse(new Bom))
        .generate()
  }

  private def toBom(report: ConfigurationReport): Bom = {
    val bom = new Bom
    bom.setSerialNumber(UUID.randomUUID().toString)

    val modules = report
      .modules
      .map(ModuleDetails.moduleDetails)

    val rootDependency = new Dependency(buildRootPackageUrl.canonicalize())

    modules.foreach(module => {
      module.callers.foreach(caller => {
        if (project.organization.equalsIgnoreCase(caller.organization) &&
          project.name.equalsIgnoreCase(caller.name)
        ) {
          rootDependency.addDependency(module.dependency)
        } else {
          modules
            .find(callerModule =>
              callerModule.component.getGroup.equalsIgnoreCase(caller.organization) &&
                callerModule.component.getName.equalsIgnoreCase(caller.name))
            .foreach(_.dependency.addDependency(module.dependency))
        }
      })
    })

    bom.setMetadata(buildMetadata)
    bom.addDependency(rootDependency)

    modules.foreach(module => {
      bom.addComponent(module.component)
      bom.addDependency(module.dependency)
    })

    bom
  }

  private def buildRootPackageUrl =
    new PackageURL(
      PackageURL.StandardTypes.MAVEN,
      project.organization,
      project.name,
      project.revision,
      null,
      null)

  private def buildMetadata: Metadata = {
    val metadata = new Metadata
    metadata.setTimestamp(new Date(LocalDate.now().toEpochDay))
    val component = new Component
    component.setGroup(project.organization)
    component.setName(project.name)
    component.setVersion(project.revision)
    component.setPurl(buildRootPackageUrl)
    component.setBomRef(component.getPurl)
    metadata.setComponent(component)
    metadata
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
