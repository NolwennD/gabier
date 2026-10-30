plugins {
  application
}

dependencies {
  testImplementation(platform(libs.junit.bom))
  testImplementation(libs.junit.jupiter)
  testRuntimeOnly(libs.junit.platform.launcher)
}

application {
  // The start script of `installDist` puts the jars on the module path and runs this module: what the self-check proves.
  mainModule = "fr.craft.gabier.daemon"
  mainClass = "fr.craft.gabier.daemon.Main"
}

// Starts the installed daemon (not the classes of the build): what a user runs, on the module path. A self-check run
// on the class path would pass where Gson fails on the module path, so this is the one that counts.
val selfCheck by tasks.registering(Exec::class) {
  group = "verification"
  description = "Runs the installed daemon with --self-check, on the module path."
  dependsOn(tasks.installDist)
  executable = layout.buildDirectory.file("install/${project.name}/bin/${project.name}").get().asFile.path
  args("--self-check")
  // The start script runs the java of JAVA_HOME: the Java 25 of the build, not the JDK that happens to run Gradle.
  environment("JAVA_HOME", javaToolchains.launcherFor(java.toolchain).get().metadata.installationPath.asFile.path)
}

tasks.check {
  dependsOn(selfCheck)
}
