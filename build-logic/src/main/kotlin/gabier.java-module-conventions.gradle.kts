plugins {
  `java-library`
  jacoco
  id("org.gradlex.extra-java-module-info")
}

// Every module is built for Java 25, the daemon included: the level of the native image, and the one the user chose.
java {
  toolchain {
    languageVersion = JavaLanguageVersion.of(25)
  }
}

// Third-party jars that are not real modules, given a descriptor here and not in the module that uses them: the
// descriptor is the same for any later module that needs the same jar, and the build fails on any other jar that is not
// a module (no module-info) or only an automatic one, instead of putting it on the module path as a guess.
extraJavaModuleInfo {
  failOnMissingModuleInfo = true
  failOnAutomaticModules = true

  // The LSP4J 1.0.0 jars have no module-info and no Automatic-Module-Name: their automatic names come from the file
  // names. Their descriptors are not closed (no closeModule()): Gson reads and writes their fields by reflection, which
  // needs the packages open at run time.
  module("org.eclipse.lsp4j:org.eclipse.lsp4j", "org.eclipse.lsp4j") {
    exportAllPackages()
    requiresTransitive("org.eclipse.lsp4j.jsonrpc")
    requires("com.google.gson")
  }
  module("org.eclipse.lsp4j:org.eclipse.lsp4j.jsonrpc", "org.eclipse.lsp4j.jsonrpc") {
    exportAllPackages()
    requires("com.google.gson")
    // RemoteEndpoint and four other classes log with java.util.logging: without this the module does not read it.
    requires("java.logging")
  }
}

tasks.compileJava {
  options.release = 25
}

tasks.withType<Test>().configureEach {
  useJUnitPlatform()
}

// Only the unit tests feed the coverage report: a later integration suite is not mixed into it.
tasks.test {
  finalizedBy(tasks.jacocoTestReport)
}

// The version catalog is registered on the project after this plugin is applied.
afterEvaluate {
  jacoco {
    toolVersion = extensions.getByType<VersionCatalogsExtension>().named("libs").findVersion("jacoco").get().requiredVersion
  }
}

// Not redundant: run alone, the report would otherwise be skipped for lack of execution data.
tasks.jacocoTestReport {
  dependsOn(tasks.test)
}

tasks.jacocoTestCoverageVerification {
  // Not redundant: run alone, the verification would otherwise read an empty execution file and fail.
  dependsOn(tasks.test)
  // Without tests the test task is NO-SOURCE, there is no execution data, and Gradle would skip the verification:
  // a module without tests would then pass. Verify anyway, against an empty execution data file: every class that
  // holds code is reported as missed, while a class without code (an annotation) has nothing to miss.
  setOnlyIf { true }
  doFirst {
    executionData.files.forEach { file ->
      if (!file.exists()) {
        file.parentFile.mkdirs()
        file.createNewFile()
      }
    }
  }
  violationRules {
    rule {
      element = "CLASS"
      limit {
        counter = "BRANCH"
        value = "MISSEDCOUNT"
        maximum = 0.toBigDecimal()
      }
      limit {
        counter = "LINE"
        value = "MISSEDCOUNT"
        maximum = 0.toBigDecimal()
      }
    }
  }
}

tasks.check {
  dependsOn(tasks.jacocoTestCoverageVerification)
}
