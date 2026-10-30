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

// The probe against a real jdtls: an integration test, in a source set of its own so that the unit tests, their coverage
// and `check` never start jdtls. It sees the same dependencies as the unit tests (JUnit, LSP4J, Gson).
val integrationTest = sourceSets.create("integrationTest")
configurations["integrationTestImplementation"].extendsFrom(configurations.testImplementation.get())
configurations["integrationTestRuntimeOnly"].extendsFrom(configurations.testRuntimeOnly.get())

tasks.register<Test>("jdtlsProbe") {
  group = "verification"
  description = "Starts a real jdtls (GABIER_JDTLS) on this project and waits until it has imported every module."
  testClassesDirs = integrationTest.output.classesDirs
  classpath = integrationTest.runtimeClasspath
  // The checkout whose modules jdtls must import; GABIER_JDTLS is the jdtls launcher, which mise exports.
  systemProperty("gabier.projectRoot", rootProject.projectDir.path)
  providers.environmentVariable("GABIER_JDTLS").orNull?.let { environment("GABIER_JDTLS", it) }
  // The coverage gate is about the unit tests: the JaCoCo plugin would otherwise attach its agent to every Test task.
  extensions.configure<JacocoTaskExtension> { isEnabled = false }
  // It talks to a real server: never up to date, and the output (pid, status, projects) is the evidence.
  outputs.upToDateWhen { false }
  testLogging {
    showStandardStreams = true
    // The message of a failure says what the probe was waiting for.
    exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
  }
}
