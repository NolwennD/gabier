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
