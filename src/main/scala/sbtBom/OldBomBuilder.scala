package sbtBom

import com.github.packageurl.PackageURL
import sbt.librarymanagement.{ConfigurationReport, ModuleReport}
import org.cyclonedx.model.LicenseChoice
import org.cyclonedx.CycloneDxSchema
import org.cyclonedx.util.{BomUtils, LicenseResolver}
import sbt.File

import scala.collection.JavaConverters.asScalaBufferConverter
import scala.xml.{Elem, Node}

class OldBomBuilder(reportOption: Option[ConfigurationReport], ignoreModules: Seq[ModuleReport]) {
  def build: Elem = {
    <bom xmlns="http://cyclonedx.org/schema/bom/1.1" version="1">
      <components>
        { reportOption.map(buildComponents(_, ignoreModules)).getOrElse(Seq()) }
      </components>
    </bom>
  }

  private def buildComponents(
    report: ConfigurationReport,
    ignoreModules: Seq[ModuleReport]
  ): Seq[Elem] = {
    report
      .modules
      .filterNot(module => ignoreModules.exists( ignoreModule =>
        ignoreModule.module.organization.equals(module.module.organization) &&
          ignoreModule.module.name.equals(module.module.name) &&
          ignoreModule.module.revision.equals(module.module.revision)
      ))
      .map(buildModule)
  }

  private def buildPurl(organisation: String, artifactId: String, revision: String): String = {
    new PackageURL(PackageURL.StandardTypes.MAVEN, organisation, artifactId, revision, null, null).canonicalize()
  }

  private def buildHashes(artefact: File): Elem = {
    <hashes>
      {
        BomUtils.calculateHashes(artefact, CycloneDxSchema.Version.VERSION_11)
          .asScala
          .map(hash => <hash alg={hash.getAlgorithm}>{ hash.getValue }</hash>)
      }
    </hashes>
  }

  private def buildModule(report: ModuleReport): Elem = {
    <component type="library">
      <group>{ report.module.organization }</group>
      <name>{ report.module.name }</name>
      <version>{ report.module.revision }</version>
      {
      report.artifacts
        .find(artifact => artifact._1.`type`.equalsIgnoreCase("jar"))
        .map(artefact => buildHashes(artefact._2))
        .orNull
      }
      <licenses>{ buildLicenses(report.licenses) }</licenses>
      <purl>{ buildPurl(report.module.organization, report.module.name, report.module.revision) }</purl>
      <modified>{ false }</modified>
    </component>
  }

  private def buildLicenses(licenses: Seq[(String, Option[String])]): Seq[Node] =
    if (licenses.isEmpty) {
      unlicensed
    } else {
      licenses.map(buildLicense)
    }

  private val unlicensed = {
    <license>
      <id>Unlicense</id>
    </license>
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

  private def buildLicense(license: (String, Option[String])): Elem = {
    val licenseName = license._1
    val licenseUrl = license._2
    val licenseChoice = new LicenseChoice
    var resolved = false

    if (licenseName != null) {
      resolved = resolveLicense(licenseName, licenseChoice)
    }

    if (!resolved && licenseUrl != null && licenseUrl.isDefined) {
      resolved = resolveLicense(licenseUrl.get, licenseChoice)
    }

    if (!resolved) {
      <license>
        <name>{ licenseName }</name>
      </license>
    } else {
      <license>
        <id>{ licenseChoice.getLicenses.get(0).getId }</id>
      </license>
    }
  }
}
