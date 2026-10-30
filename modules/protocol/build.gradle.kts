java {
  toolchain {
    languageVersion = JavaLanguageVersion.of(25)
  }
}

tasks.compileJava {
  options.release = 25
}

dependencies {
  testImplementation(platform(libs.junit.bom))
  testImplementation(libs.junit.jupiter)
  testRuntimeOnly(libs.junit.platform.launcher)
}
