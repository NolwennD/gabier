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
  mainModule = "eu.gabier.daemon"
  mainClass = "eu.gabier.daemon.Main"
}

// The jdtls the daemon looks for when GABIER_JDTLS is not set: the version that `mise.toml` pins (`jdtls_version`), written
// in the resource `jdtls.properties` that `JdtlsLocation` reads. The number lives in `mise.toml` alone.
val jdtlsVersion = providers
  .fileContents(rootProject.layout.projectDirectory.file("mise.toml"))
  .asText
  .map { text ->
    Regex("""(?m)^jdtls_version\s*=\s*"([^"]+)"""").find(text)?.groupValues?.get(1)
      ?: throw GradleException("mise.toml has no jdtls_version")
  }

tasks.processResources {
  inputs.property("jdtlsVersion", jdtlsVersion)
  filesMatching("**/jdtls.properties") {
    filter<org.apache.tools.ant.filters.ReplaceTokens>("tokens" to mapOf("version" to jdtlsVersion.get()))
  }
}

// The version the build was given, for the test that proves it reaches JdtlsLocation.
tasks.test {
  systemProperty("gabier.jdtlsVersion", jdtlsVersion.get())
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
// Its one piece of logic, what it refuses to write over, is a unit test of the daemon (the coverage gate looks at the
// classes of the main source set only).
dependencies {
  testImplementation(fakeServed.output)
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
  mainClass = "eu.gabier.daemon.FakeServedMain"
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

// The real jdtls: the replay of the contract scenarios of the core and the installed daemon.
tasks.register<Test>("jdtlsProbe") {
  group = "verification"
  description = "Starts a real jdtls (GABIER_JDTLS): the replay of the contract scenarios and the installed daemon."
  testClassesDirs = integrationTest.output.classesDirs
  classpath = integrationTest.runtimeClasspath
  // The coverage gate is about the unit tests: the JaCoCo plugin would otherwise attach its agent to every Test task.
  extensions.configure<JacocoTaskExtension> { isEnabled = false }
  // GABIER_JDTLS is the jdtls launcher, which mise exports.
  // The installed daemon (what a user runs): InstalledDaemonTest serves a small project with it.
  dependsOn(tasks.installDist)
  systemProperty(
    "gabier.daemon",
    layout.buildDirectory.file("install/${application.applicationName}/bin/${application.applicationName}").get().asFile.path,
  )
  providers.environmentVariable("GABIER_JDTLS").orNull?.let { environment("GABIER_JDTLS", it) }
  // The pinned version, which the installed daemon looks for under the home folder when GABIER_JDTLS is not set.
  systemProperty("gabier.jdtlsVersion", jdtlsVersion.get())
  // It talks to a real server: never up to date, and the output (pid, status, projects) is the evidence.
  outputs.upToDateWhen { false }
  testLogging {
    showStandardStreams = true
    // The message of a failure says what the probe was waiting for.
    exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
  }
}
