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

javaModules {
  directory("modules") {
    group = "fr.craft.gabier"
    plugin("gabier.java-module-conventions")
  }
}
