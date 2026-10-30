# The cli module prints its version

Opened: 30-10-2026 00:46

Add the `cli` module (Java 25, JDK-only) that depends on `protocol` and answers `gabier --version`
with the protocol version. An unknown argument prints a short usage and fails.

Done when:
- the unit tests for `--version` and for an unknown argument pass;
- the module's `module-info` requires only our own modules, `protocol` and, for the coverage exclusion of the entry
  point (`main` ends in `System.exit`), `shared`: no third-party module;
- `bin/ci` passes, the coverage gate and the architecture tests included.

Depends on: gradle-skeleton-and-protocol-module, architecture-tests-for-a-pure-java-core (the architecture tests
cover this module too, and both edit the build files).

## Resolution

Done in d15458d, by a subagent. The module `fr.craft.gabier.cli` (Java 25) requires only `fr.craft.gabier.protocol` and
`fr.craft.gabier.shared`. `Gabier.run(args, out, err)` holds the logic; `main`, one line ending in `System.exit`, is the
only thing excluded from coverage, with a real reason. Only exactly `--version` succeeds: it prints `gabier 0.1` on the
standard output and returns 0; no argument, an unknown argument, `--version` followed by anything, or anything followed
by `--version` print `usage: gabier --version` on the standard error, nothing on the standard output, and return 2.

Checked by the subagent, `bin/ci`: format passed, build passed, test passed, native and jdtls skipped; `cli tests=9
failures=0`, `protocol tests=4`, `architecture tests=11`. TDD: red because `Gabier` did not exist, then 7 of 9 tests
failing on a stub, then green.

Outside the text of the ticket, and right: the native stage of `bin/ci` was keyed on the presence of the `cli` module,
which has no native build yet, so it failed on a task that does not exist.

Neutral review, done once: nothing blocking. What became of the suggestions:
- `requires fr.craft.gabier.shared` is only needed for the annotation: made `requires static` by the supervisor, without
  a ticket (a very small task), so that `shared` stays out of the runtime and of the native image;
- the native stage of `bin/ci` runs whenever any module build file mentions `graalvm`: the supervisor's modular rewrite
  of `bin/ci` keys it on `modules/cli/build.gradle.kts` only;
- the test's `gabier 0.1` repeats the value of `ProtocolVersion.CURRENT`, so it breaks on every protocol bump: accepted,
  deriving it would make the test vacuous;
- three tests hold several assertions where the others hold one, a closed standard output still gives status 0 because
  `PrintStream` swallows write errors, and status 2 is documented only by the tests: left.

To remember: the native executable is another ticket; it will apply the GraalVM plugin in `modules/cli/build.gradle.kts`,
which activates the native stage of `bin/ci` by itself.
