pluginManagement {
  includeBuild("build-logic")
}

plugins {
  id("org.gradlex.java-module-dependencies") version "1.13.2"
  id("gabier.java-module-conventions") apply false
}

dependencyResolutionManagement {
  repositories {
    mavenCentral()
  }
}

rootProject.name = "gabier"

// Not a module: the architecture tests, which need the classes of every module on one classpath.
include("architecture")

javaModules {
  directory("modules") {
    group = "fr.craft.gabier"
    plugin("gabier.java-module-conventions")
  }
}
