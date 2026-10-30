# Gradle skeleton and the protocol module

Opened: 30-10-2026 00:45

Set up the Gradle build (wrapper 9.8.2, GradleX plugins, one subproject per JPMS module under
`modules/`) with a first tiny module, `protocol`, built for Java 25. It holds a `ProtocolVersion`
record, just enough to have something to test.

Done when:
- `./gradlew :protocol:test` passes;
- jdtls build files (`.project`, `.classpath`, `.settings/`) and Gradle output are git-ignored.
