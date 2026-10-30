plugins {
  `java-library`
  id("org.gradlex.extra-java-module-info")
}

tasks.withType<Test>().configureEach {
  useJUnitPlatform()
}
