# The cli builds as a native executable that runs standalone

Opened: 30-10-2026 02:46

Build the `cli` module as a GraalVM 25 native executable (the `org.graalvm.buildtools.native` plugin, version in the
catalog), with the fallback to a JVM disabled so that a half-working image fails the build instead of passing silently.
Applying the plugin in `modules/cli/build.gradle.kts` is also what switches on the `native` stage of `bin/ci`.

Done when:
- `./gradlew :cli:nativeCompile` produces a binary that prints `gabier 0.1` when it is copied alone into an empty
  directory and run with an empty environment and no JDK on the path (the `native` stage of `bin/ci` does exactly that
  and passes, not skipped);
- the glibc the binary needs is written down in the resolution of this issue;
- the other stages of `bin/ci` still pass, the coverage gate and the architecture tests included.

## Resolution

Done in 3603ed1, by a subagent. The plugin `org.graalvm.buildtools.native` 1.1.18 (catalog) is applied in
`modules/cli/build.gradle.kts`: image `gabier`, main class `fr.craft.gabier.cli.Gabier`, `--no-fallback` and
`-march=compatibility` (baseline x86-64, so the binary runs on older CPUs), `sharedLibrary = false` (with `java-library`
the plugin otherwise builds a shared library, not an executable). This switches on the `native` stage of `bin/ci`.

Checked by the subagent, `bin/ci`: format, build and test passed (`cli tests=9`, `protocol tests=4`, `architecture
tests=11`, no failure); `native` ran and passed: the binary, copied alone into an empty directory and run with an empty
environment, prints `gabier 0.1`, exit 0. The binary is 6,621,448 bytes (6.3 MiB), stripped, dynamically linked against
`libc.so.6` only. glibc: the highest versioned symbol is `GLIBC_2.34`, but that is the glibc of the build host
(Ubuntu, 2.39); a binary built elsewhere has its own floor. The compile took 1 min 11 s.

Neutral review, done once: nothing blocking, and it ran the binary itself under `env -i`. What became of the findings:
- the toolchain selection: `JvmVendorSpec.GRAAL_VM` found no JDK because the SDKMAN GraalVM 25.0.4 reports its vendor as
  "Oracle"; the launcher asks for Java 25 and `nativeImageCapable = true` with no vendor, so any native-image capable
  JDK 25 is accepted, and the failure is early if none is installed: accepted;
- `--no-fallback` is already the default of plugin 1.1.18, `-march=compatibility` costs some vector intrinsics (no
  matter for a thin client), the image is built from the classpath so `module-info` is ignored and it works because
  `shared` is `requires static` and an annotation class missing at run time is tolerated: comments added by the
  supervisor, without a ticket;
- "several minutes" in the `verify` skill was unmeasured (it took 1 min 11 s): corrected by the supervisor;
- the `mainClass` string is unchecked and will change with the base package: added to the rename issue;
- with an empty environment the standard output is ASCII, so a non-ASCII message would be mangled: keep the messages
  ASCII or set the encoding explicitly when the cli prints something else.
