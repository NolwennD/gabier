# The modules move past Java 25

Opened: 30-10-2026 21:58
On hold: the user decides when; Java 28 is the interesting target, for structured concurrency (JEP 543), and the release
25 stays until then. ArchUnit 1.5.1 (Java 27 support) is already in.

The build compiles every module with `--release 25` and fixes the toolchain of `:architecture` at 25, because ArchUnit
1.4.2 skipped Java 27 classes without saying so (the user's decision, recorded in the issue
`architecture-tests-for-a-pure-java-core`: every module stays on 25, the daemon included). ArchUnit is on 1.5.1 now
(30-10-2026), which supports Java 27, so that reason is gone; the JDK of the project is 27 (`.sdkmanrc`), and
`.claude/rules/domain.md` asks for the idioms of the release of the module. Whichever release is chosen (27, or 28 when
it is out and its tooling supports it), this ticket reverses the decision of the release 25: its `## Resolution` says so,
and why.

Done when:
- the toolchain of `:architecture` and `options.release` of every module (`gabier.java-module-conventions`) are the
  chosen release; the comments that say "25 because of ArchUnit" are gone; the daemon and the other modules use what
  the release adds (for 28, structured concurrency, for the start of the jdtls session and the requests in flight);
- a test of `:architecture` fails if a module contributes no class to the imported classes (so a class file ArchUnit
  cannot read can no longer be skipped silently), shown once by importing a Java 27 class that the old version would skip
  (or, if that cannot be shown, say so);
- Error Prone, NullAway, Checkstyle, JaCoCo, the native image and jdtls (its own JDK) all run on the release 27 build; what
  does not is listed with its reason and left on 25 only if there is one;
- `.claude/rules/quality.md` and the README say the release; `bin/ci` green, the `native` and `jdtls` stages run.

Run it alone: it edits the build of every module.
