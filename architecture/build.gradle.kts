// Test-only project: it holds the architecture tests (ArchUnit) and no production code, so it does not take the module
// conventions (no module-info, no coverage gate). ArchUnit reads the classes of every module from one classpath, which
// no single module could give it under JPMS; the tests run in `./gradlew check` like any other.
plugins {
  java
}

java {
  toolchain {
    languageVersion = JavaLanguageVersion.of(25)
  }
}

dependencies {
  // Every module under modules/, a new one (cli, daemon…) included: nothing to add here when a module is created.
  rootProject.subprojects
    .filter { it.projectDir.parentFile.name == "modules" }
    .forEach { testImplementation(project(it.path)) }

  testImplementation(platform(libs.junit.bom))
  testImplementation(libs.junit.jupiter)
  testImplementation(libs.assertj.core)
  testImplementation(libs.archunit)
  testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.test {
  useJUnitPlatform()
}
