plugins {
  alias(libs.plugins.graalvm.native)
}

dependencies {
  testImplementation(platform(libs.junit.bom))
  testImplementation(libs.junit.jupiter)
  testRuntimeOnly(libs.junit.platform.launcher)
}

graalvmNative {
  binaries {
    named("main") {
      // A GraalVM 25, found among the toolchains (the SDKMAN! candidates included). It is asked for what it can do
      // (native-image) and not for a vendor: JvmVendorSpec.GRAAL_VM would exclude the Oracle GraalVM, which Gradle
      // reports as vendor "Oracle".
      javaLauncher = javaToolchains.launcherFor {
        languageVersion = JavaLanguageVersion.of(25)
        nativeImageCapable = true
      }
      imageName = "gabier"
      // The plugin builds a shared library (--shared) for a module with the java-library plugin: this one is a program.
      sharedLibrary = false
      mainClass = "fr.craft.gabier.cli.Gabier"
      // No fallback: an image that cannot be built natively fails the build instead of quietly needing a JVM.
      buildArgs.add("--no-fallback")
      // The baseline of the architecture instead of the host CPU: the binary runs on any machine of its architecture.
      buildArgs.add("-march=compatibility")
    }
  }
}
