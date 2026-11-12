# diagnostics does not report the testFixtures of another module as inaccessible

Opened: 13-11-2026 00:17

A friction met while using gabier (reported by the worker of the ticket 12-11-2026-2147): `gabier diagnostics` on a test file of
a module that uses the testFixtures of another module (for instance `eu.gabier.editing.fixtures` of `modules/core`, used by
`modules/daemon`) answers "not accessible" errors that the Gradle build does not give. An agent that trusts `gabier
diagnostics` then fixes what is not broken, or goes back to Gradle.

Done when:
- The case is reproduced on this repository (a test of `modules/daemon` that imports a class of the testFixtures of
  `modules/core`): the command, the file and the answer are written in the resolution, with what the Gradle build says of the
  same file.
- The cause is found: either the project the language server imports lacks the testFixtures of the other module (a matter of how
  jdtls imports the Gradle project, and of the settings of `JdtlsSettings` or of the import it is given), or it is a case jdtls
  gets wrong. In the first case `gabier diagnostics` answers what the build answers, proven by a replay on a project with a
  testFixtures dependency (skill `add-scenario`); in the second, the limit is in the help of `diagnostics` and reported
  upstream (workflow rule "jdtls is never fought"), and no check of gabier guesses it in advance.
