plugins {
  application
}

dependencies {
  testImplementation(platform(libs.junit.bom))
  testImplementation(libs.junit.jupiter)
  testImplementation(libs.assertj.core)
  testRuntimeOnly(libs.junit.platform.launcher)
}

application {
  // The start script of `installDist` puts the jars on the module path and runs this module: what the self-check proves.
  mainModule = "fr.craft.gabier.daemon"
  mainClass = "fr.craft.gabier.daemon.Main"
}

// Starts the installed daemon (not the classes of the build): what a user runs, on the module path. A self-check run
// on the class path would pass where Gson fails on the module path, so this is the one that counts.
val selfCheck = tasks.register<Exec>("selfCheck") {
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

// The integration tests, in a source set of their own so that the unit tests and their coverage stay apart. It sees the
// same dependencies as the unit tests (JUnit, LSP4J, Gson) and the classes of the daemon (the jdtls adapter that the
// probe starts jdtls through, on the class path like the tests). Two Test tasks run it, split by class name: the quick
// tests, which start no jdtls and run in `check`, and the real probe, which starts jdtls and runs only on demand.
val integrationTest = sourceSets.create("integrationTest")
dependencies {
  "integrationTestImplementation"(sourceSets.main.get().output)
}
configurations["integrationTestImplementation"].extendsFrom(configurations.testImplementation.get())
configurations["integrationTestRuntimeOnly"].extendsFrom(configurations.testRuntimeOnly.get())

// The one class that starts a real jdtls (it needs GABIER_JDTLS). Every other class of the source set is a quick test:
// a class that starts jdtls must be this one, or this constant must name it too, otherwise `check` runs it and fails at
// once for lack of GABIER_JDTLS. The adapter itself (JdtlsSession) is tested without jdtls, in the unit tests.
val realProbe = "fr.craft.gabier.daemon.JdtlsProbeTest"

fun Test.runIntegrationTests() {
  group = "verification"
  testClassesDirs = integrationTest.output.classesDirs
  classpath = integrationTest.runtimeClasspath
  // The coverage gate is about the unit tests: the JaCoCo plugin would otherwise attach its agent to every Test task.
  extensions.configure<JacocoTaskExtension> { isEnabled = false }
}

// Cheap, and in `check`: what only the probe needs (the store of diagnostics, the wait for a quiet jdtls), tested with
// child processes and hand-made messages, so that these assertions cannot go stale unnoticed. Up to date when nothing
// changed, like the unit tests.
val quickProbeTest = tasks.register<Test>("quickProbeTest") {
  description = "Runs the integration tests that start no jdtls (everything but JdtlsProbeTest)."
  runIntegrationTests()
  filter.excludeTestsMatching(realProbe)
  testLogging {
    events("passed", "failed", "skipped")
    exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
  }
}

tasks.check {
  dependsOn(quickProbeTest)
}

// The real probe against a real jdtls. `check` only compiles it (through quickProbeTest, which needs the classes).
tasks.register<Test>("jdtlsProbe") {
  description = "Starts a real jdtls (GABIER_JDTLS) on this project and waits until it has imported every module."
  runIntegrationTests()
  filter.includeTestsMatching(realProbe)
  // The checkout whose modules jdtls must import; GABIER_JDTLS is the jdtls launcher, which mise exports.
  systemProperty("gabier.projectRoot", rootProject.projectDir.path)
  providers.environmentVariable("GABIER_JDTLS").orNull?.let { environment("GABIER_JDTLS", it) }
  // It talks to a real server: never up to date, and the output (pid, status, projects) is the evidence.
  outputs.upToDateWhen { false }
  testLogging {
    showStandardStreams = true
    // The message of a failure says what the probe was waiting for.
    exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
  }
}
