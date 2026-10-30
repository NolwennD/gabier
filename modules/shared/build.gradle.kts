java {
  toolchain {
    languageVersion = JavaLanguageVersion.of(25)
  }
}

tasks.compileJava {
  options.release = 25
}
