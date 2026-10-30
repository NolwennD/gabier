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
