# The probe checks the diagnostics, including a deliberate error

Opened: 30-10-2026 04:41

Extend the jdtls probe: open the `module-info.java` and the main class of each module, wait for the diagnostics jdtls
publishes for them, and fail if one is an error. jdtls compiles in the checkout with its own compiler, on the Java 25
modules of a Gradle build, so its diagnostics can differ from Gradle's: this is where a false error would show. As a
control, open an in-memory file with an obvious type error and expect jdtls to report it, otherwise a probe that sees
nothing would pass for nothing.

Done when:
- the probe fails with a readable list (file, line, message) when a real error is present, and passes on the clean
  project;
- every opened file received at least one publication of diagnostics, so that silence is not taken for success;
- the control error is detected;
- what jdtls says about this project is written in the resolution, false errors included: if there are any, stop and
  decide with the user between a newer jdtls, a jdtls-specific Gradle setting, or a list of tolerated errors.

Depends on: probe-fails-fast-when-jdtls-dies (both edit the probe), use-assertj-for-assertions.

## Resolution

Done in fff77a9. The probe opens (`didOpen`) the `module-info.java` and every `src/main/java` source of each module,
and the sources of `architecture`, waits for a publication of diagnostics for each (an empty list counts, silence fails
and names the file, inside the probe's one shared deadline), and fails with `file:line: message` lines if one is an
error. A control file (not on disk, `int control = "not an int";` next to a class of `cli`) must get an error on its
line mentioning `String` and `int`. The store of publications is `Diagnostics`, with its own tests.

Checked by the subagent: `bin/ci` green with `GABIER_JDTLS` set (format 1s, build 9s, test 23s, native 84s, jdtls
53s). A deliberate `int OK = "zero";` in `Main.java` made the probe fail with
`modules/daemon/src/main/java/fr/craft/gabier/daemon/Main.java:10: Type mismatch: cannot convert from String to int`
(reverted since).

**What jdtls 1.63.0 says about this project**: nothing. Ten files opened (`cli`, `daemon`, `protocol`, `shared`,
`architecture`; `daemon`'s `module-info.java` included, so LSP4J and Gson resolve as modules), each with exactly one
publication, empty: no error, no warning, no false error to tolerate. jdtls lists the projects `architecture`,
`build-logic`, `modules/cli`, `modules/daemon`, `modules/protocol`, `modules/shared`; `build-logic` is not opened.
LSP4J logs `Unsupported notification method: language/eventNotification`, which is harmless.

Review (one neutral reviewer). Two findings, argued as blocking, that I did not take as an unmet `Done when` point:
the probe judges the latest publication after the first one of each file, and jdtls publishes in stages, so a late
publication with a real error could come after the verdict, and a publication sent between `expect` and the `didOpen`
of a file could be taken for its answer. The injected error was detected in the real run, and each file got one
publication, so the points are met as observed; the weakness is a latent risk, opened as
`probe-judges-settled-diagnostics`, together with `Diagnostics.pathOf` throwing on a non-`file:` URI (`jdt://`) in
the reader thread. Left as matters of taste: the package of the control file when its neighbour sits directly in
`src/main/java`; the sources of `architecture` silently skipped when jdtls does not import it.
