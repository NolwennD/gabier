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
  // The name of the start script and of the distribution: what a user types (bin/gabier-daemon), not the project name.
  applicationName = "gabier-daemon"
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
  executable = layout.buildDirectory.file("install/${application.applicationName}/bin/${application.applicationName}").get().asFile.path
  args("--self-check")
  // The start script runs the java of JAVA_HOME: the Java 25 of the build, not the JDK that happens to run Gradle.
  environment("JAVA_HOME", javaToolchains.launcherFor(java.toolchain).get().metadata.installationPath.asFile.path)
}

tasks.check {
  dependsOn(selfCheck)
}

// The daemon served by the fake language server of the core, for the `native` stage of `bin/ci` (jdtls stays out of it).
// It is a distribution of its own, `installFakeServed`, on the class path: the fake is test code and must not ship in the
// daemon a user installs, nor be reachable by a request or an option of that one. Its main class is in the source set
// `fakeServed`, which is not under the coverage gate (it only wires the fake the way the unit tests do).
val fakeServed = sourceSets.create("fakeServed")
dependencies {
  "fakeServedCompileOnly"(sourceSets.main.get().output)
  "fakeServedImplementation"(testFixtures(project(":core")))
}
configurations["fakeServedImplementation"].extendsFrom(configurations.implementation.get())
configurations["fakeServedRuntimeOnly"].extendsFrom(configurations.runtimeOnly.get())

val fakeServedJar = tasks.register<Jar>("fakeServedJar") {
  archiveClassifier = "fake-served"
  from(fakeServed.output)
}

val fakeServedLibraries = files(tasks.jar, fakeServedJar, configurations["fakeServedRuntimeClasspath"])

val fakeServedScripts = tasks.register<CreateStartScripts>("fakeServedScripts") {
  applicationName = "gabier-daemon-fake"
  mainClass = "fr.craft.gabier.daemon.FakeServedMain"
  outputDir = layout.buildDirectory.dir("fake-served-scripts").get().asFile
  classpath = fakeServedLibraries
}

tasks.register<Sync>("installFakeServed") {
  group = "distribution"
  description = "Installs the daemon served by the fake language server (bin/gabier-daemon-fake <project folder>)."
  into(layout.buildDirectory.dir("install/gabier-daemon-fake"))
  from(fakeServedScripts) { into("bin") }
  from(fakeServedLibraries) { into("lib") }
}

tasks.check {
  dependsOn(tasks.named(fakeServed.classesTaskName))
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
