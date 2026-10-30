# The modules move to Java 27, with ArchUnit 1.5.1

Opened: 30-10-2026 21:58

The build compiles every module with `--release 25` and fixes the toolchain of `:architecture` at 25, because ArchUnit
1.4.2 skips Java 27 classes without saying so (the user's decision, recorded in the issue
`architecture-tests-for-a-pure-java-core`: every module stays on 25, the daemon included). ArchUnit 1.5.1 supports
Java 27, so the reason is gone; the JDK of the project is already 27 (`.sdkmanrc`), and `.claude/rules/domain.md` asks
for the idioms of the release of the module. This ticket reverses that decision: its `## Resolution` says so, and why.

Done when:
- `archunit` is `1.5.1` in `gradle/libs.versions.toml`; the toolchain of `:architecture` and `options.release` of every
  module (`gabier.java-module-conventions`) are 27; the comments that say "25 because of ArchUnit" are gone;
- a test of `:architecture` fails if a module contributes no class to the imported classes (so a class file ArchUnit
  cannot read can no longer be skipped silently), shown once by importing a Java 27 class that the old version would skip
  (or, if that cannot be shown, say so);
- Error Prone, NullAway, Checkstyle, JaCoCo, the native image and jdtls (its own JDK) all run on the release 27 build; what
  does not is listed with its reason and left on 25 only if there is one;
- `.claude/rules/quality.md` and the README say the release; `bin/ci` green, the `native` and `jdtls` stages run.

Run it alone: it edits the build of every module (not before the batches in flight have merged).
