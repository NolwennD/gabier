plugins {
  alias(libs.plugins.graalvm.native)
}

dependencies {
  testImplementation(platform(libs.junit.bom))
  testImplementation(libs.junit.jupiter)
  testImplementation(libs.assertj.core)
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
      // No fallback: an image that cannot be built natively fails the build instead of quietly needing a JVM. This is
      // already the plugin's default; it is written down to keep the intent.
      buildArgs.add("--no-fallback")
      // The baseline of the architecture instead of the host CPU: the binary runs on any machine of its architecture.
      // The cost is some vector intrinsics, which does not matter for a thin client.
      buildArgs.add("-march=compatibility")
      // The image is built from the classpath, so module-info is ignored. `org.jspecify` and the NullAway annotations
      // are `requires static` (not needed at run time) and absent from the runtime classpath. `shared` is there, as
      // `protocol` requires it (ProtocolVersion validates with Assert): the cli reaches it at run time through protocol.
    }
  }
}
