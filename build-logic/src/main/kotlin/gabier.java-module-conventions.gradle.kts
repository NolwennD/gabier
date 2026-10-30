plugins {
  `java-library`
  jacoco
  id("org.gradlex.extra-java-module-info")
}

tasks.withType<Test>().configureEach {
  useJUnitPlatform()
  finalizedBy(tasks.jacocoTestReport)
}

// The version catalog is registered on the project after this plugin is applied.
afterEvaluate {
  jacoco {
    toolVersion = extensions.getByType<VersionCatalogsExtension>().named("libs").findVersion("jacoco").get().requiredVersion
  }
}

tasks.jacocoTestReport {
  dependsOn(tasks.test)
}

tasks.jacocoTestCoverageVerification {
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
