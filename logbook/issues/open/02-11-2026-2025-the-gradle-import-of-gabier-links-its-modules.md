# A test shows that the Gradle import of gabier links its modules

Opened: 02-11-2026 20:25
On hold: a finding of the review of `move-rewrites-references-in-other-modules`; it breaks no `Done when` point.

The fix of that ticket is `skipLocalJars = true` in `build-logic/src/main/kotlin/gabier.java-module-conventions.gradle.kts`,
but the replay writes the project links by hand (`EclipseProject`, `REQUIRED_PROJECT` in `.classpath`), so removing the
line leaves every gate green while `gabier move` again misses the other modules. Reproduce: remove the line, run
`./gradlew jdtlsProbe`; it stays green.

Done when:
- removing `skipLocalJars = true` fails a test or a `bin/ci` stage that names the lost project link;
- `bin/ci` passes.
