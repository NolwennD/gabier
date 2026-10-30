# Gradle skeleton and the protocol module

Opened: 30-10-2026 00:45

Set up the Gradle build (wrapper 9.8.2, GradleX plugins, one subproject per JPMS module under
`modules/`) with a first tiny module, `protocol`, built for Java 25. It holds a `ProtocolVersion`
record, just enough to have something to test.

Done when:
- `./gradlew :protocol:test` passes;
- jdtls build files (`.project`, `.classpath`, `.settings/`) and Gradle output are git-ignored.

## Resolution

Done in 4a29cb9: wrapper 9.8.2, the GradleX settings plugin, a `gabier.java-module-conventions`
plugin in `build-logic`, and the `protocol` module (Java 25) with `ProtocolVersion` and its four
tests. Both points were checked: the tests pass and `git check-ignore` confirms the ignored files.

To remember for the next modules:
- the GradleX settings plugin takes its version in `settings.gradle.kts`, not from `build-logic`;
- the convention plugin must be declared there too, with `apply false`;
- Maven Central is declared once, in `dependencyResolutionManagement`;
- `javaModules` only sees a folder that holds a `module-info.java`.
