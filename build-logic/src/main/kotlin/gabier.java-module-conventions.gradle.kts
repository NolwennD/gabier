import net.ltgt.gradle.errorprone.errorprone

plugins {
  `java-library`
  jacoco
  id("org.gradlex.extra-java-module-info")
  id("net.ltgt.errorprone")
  id("gabier.checkstyle-conventions")
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

  // The test fixtures of core (src/testFixtures: the fake language server and its scenarios), for the tests of the other
  // modules. Their jar has no descriptor, and the transform above refuses a jar without one on the test runtime path of
  // the consumer. It cannot carry its own module-info.java: jdtls reports "The package ... does not exist or is empty"
  // on a second descriptor in the same project. So the descriptor is given here, like a third-party jar's. A module
  // whose fixtures are used by another one adds its own rule: <project>-test-fixtures.jar.
  module("core-test-fixtures.jar", "fr.craft.gabier.core.test.fixtures") {
    exportAllPackages()
    requiresTransitive("fr.craft.gabier.core")
  }

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

// The compiler plugins (Error Prone, NullAway) are loaded from the annotation processor path, which is a class path and
// not a module path: their jars need no module descriptor, and demanding one would fail the build on every one of them.
sourceSets.configureEach {
  extraJavaModuleInfo.deactivate(configurations.getByName(annotationProcessorConfigurationName))
}

tasks.compileJava {
  options.release = 25
}

// Null safety, checked at compile time: NullAway (through Error Prone) refuses a null where none is allowed. It checks
// what is @NullMarked only (the package-info.java of each package). Tests are not @NullMarked, so NullAway would check
// nothing there: Error Prone runs on the main code only (compileJava), and the test source sets are compiled as before.
tasks.withType<JavaCompile>().configureEach {
  options.errorprone {
    enabled = name == "compileJava"
    error("NullAway")
    // Unused code: Error Prone only warns by default. UnusedVariable covers the private fields, the local variables and
    // the parameters (of private methods, and of the others when nothing overrides them); UnusedMethod the private methods.
    error("UnusedMethod")
    error("UnusedVariable")
    option("NullAway:JSpecifyMode", "true")
    option("NullAway:OnlyNullMarked", "true")
  }
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
  val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")
  dependencies {
    // Error Prone runs in the compiler, NullAway is one of its checks: both are on the annotation processor path.
    "errorprone"(libs.findLibrary("errorprone-core").get())
    "errorprone"(libs.findLibrary("nullaway").get())
  }
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
