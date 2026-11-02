// A real JSON parser, for the tests only: the problem objects are written by hand (the cli native image has no
// third-party library), and the tests prove that what is written is valid JSON by parsing it.
testModuleInfo {
  requires("com.google.gson")
}

dependencies {
  testImplementation(platform(libs.junit.bom))
  testImplementation(libs.junit.jupiter)
  testImplementation(libs.assertj.core)
  testRuntimeOnly(libs.junit.platform.launcher)
}

// The version of gabier, written in the resource `version.properties` that `ProtocolVersion` reads: the release workflow
// gives it, from the tag (`-PgabierVersion=0.1.0`); a build with no tag is `0.0.0-dev`. It is a property of the build and
// not the version of the project: the names of the jars of the distribution do not carry it.
val gabierVersion = providers.gradleProperty("gabierVersion").orElse("0.0.0-dev")

tasks.processResources {
  inputs.property("gabierVersion", gabierVersion)
  filesMatching("**/version.properties") {
    filter<org.apache.tools.ant.filters.ReplaceTokens>("tokens" to mapOf("version" to gabierVersion.get()))
  }
}

// The version the build was given, for the test that proves it reaches ProtocolVersion.
tasks.test {
  systemProperty("gabier.version", gabierVersion.get())
}
