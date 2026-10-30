plugins {
  `kotlin-dsl`
}

repositories {
  gradlePluginPortal()
}

dependencies {
  implementation("org.gradlex:extra-java-module-info:1.14.2")
  implementation("net.ltgt.gradle:gradle-errorprone-plugin:5.1.1")
}
