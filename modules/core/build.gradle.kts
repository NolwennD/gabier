plugins {
  // src/testFixtures: the fake language server and the contract scenarios, used by the tests of core and of the modules
  // above it (testImplementation(testFixtures(project(":core")))). See the rule for core-test-fixtures.jar in
  // gabier.java-module-conventions.
  `java-test-fixtures`
}

dependencies {
  testImplementation(platform(libs.junit.bom))
  testImplementation(libs.junit.jupiter)
  testImplementation(libs.assertj.core)
  testRuntimeOnly(libs.junit.platform.launcher)
}
