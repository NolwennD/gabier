plugins {
  application
}

dependencies {
  testImplementation(platform(libs.junit.bom))
  testImplementation(libs.junit.jupiter)
  testImplementation(libs.assertj.core)
  // The contract scenarios of the core, replayed against a real jdtls by the integration tests.
  testImplementation(testFixtures(project(":core")))
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

// The integration tests, in a source set of their own so that the unit tests and their coverage stay apart. Every one
// of them starts a real jdtls (it needs GABIER_JDTLS): `check` only compiles them, so that they cannot rot, and
// `jdtlsProbe` runs them on demand. It sees the same dependencies as the unit tests (JUnit, LSP4J, Gson, the test
// fixtures of the core) and the classes of the daemon (the jdtls adapter), on the class path like the tests. What can be
// tested without jdtls (the adapter against a fake language server) is a unit test.
val integrationTest = sourceSets.create("integrationTest")
dependencies {
  "integrationTestImplementation"(sourceSets.main.get().output)
}
configurations["integrationTestImplementation"].extendsFrom(configurations.testImplementation.get())
configurations["integrationTestRuntimeOnly"].extendsFrom(configurations.testRuntimeOnly.get())

tasks.check {
  dependsOn(tasks.named(integrationTest.classesTaskName))
}

// The real jdtls: the probe on this checkout and the replay of the contract scenarios of the core.
tasks.register<Test>("jdtlsProbe") {
  group = "verification"
  description = "Starts a real jdtls (GABIER_JDTLS): the probe on this project and the replay of the contract scenarios."
  testClassesDirs = integrationTest.output.classesDirs
  classpath = integrationTest.runtimeClasspath
  // The coverage gate is about the unit tests: the JaCoCo plugin would otherwise attach its agent to every Test task.
  extensions.configure<JacocoTaskExtension> { isEnabled = false }
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
