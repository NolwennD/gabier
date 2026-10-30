plugins {
  checkstyle
}

// Checkstyle keeps the conventions the formatter cannot (names, Javadoc, declaration order...): one configuration for the
// whole repository, on the main and the test code of every project that applies this plugin (every module, and the
// architecture tests). It runs in the Gradle build, in its own configuration: not on the module path of the product.
checkstyle {
  configFile = rootProject.file("config/checkstyle/checkstyle.xml")
  isIgnoreFailures = false
  maxWarnings = 0
  isShowViolations = true
}

// The version catalog is registered on the project after this plugin is applied.
afterEvaluate {
  checkstyle {
    toolVersion = extensions.getByType<VersionCatalogsExtension>().named("libs").findVersion("checkstyle").get().requiredVersion
  }
}
