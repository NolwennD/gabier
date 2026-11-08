# The CI runs the build and the tests on macOS and Windows, so that what breaks there is known

Opened: 08-11-2026 17:15

For 0.5.0 (gabier for macOS and Windows; decision of the user, 08-11-2026: no local test machine, so the runners of GitHub are
the test). Today `bin/ci` runs on Linux x86-64 only (`ci.yml`, one job). A matrix adds `macos-latest` (Apple Silicon) and
`windows-latest` and runs the stages that can run there (`format`, `build`, `test`, then `native`), each platform in its own job,
`fail-fast: false`, so that one run lists everything that breaks on each system. Inspiration: the matrix of
`linkedin-archive-explorer` (`.github/workflows/release.yml`: one runner per target, `graalvm/setup-graalvm`, `shell: bash`).

Done when:
- [ ] `ci.yml` has the jobs `bin-ci` (Linux, unchanged: it stays the required check of `main`) and `bin-ci-macos`, `bin-ci-windows`
      (not required while they are red); the environment action (`setup-build`) works on the three systems (the JDK of
      `.sdkmanrc`, the GraalVM, mise or the tools it needs there, the caches keyed on the pins and per system);
- [ ] the first run is read and each failure becomes a line of the resolution (stage, test or command, system, cause when
      known), and one issue per cause family is opened with `ticket --issue-only` (paths and sockets, the launcher of jdtls,
      shell scripts, file modes, line endings...); nothing is fixed in this ticket;
- [ ] `bin/ci` itself runs on Windows (it is a Java file with `sh` stages: say what is needed, `bash` of the runner or a Java
      port of the stage scripts); `docs` and `AGENTS.md` say that the macOS and Windows jobs are informative until 0.5.0.

## Resolution

Abandoned, not done (decision of the user, 08-11-2026): the port to macOS and Windows turned out to be more work than
expected, and it is left to an external contribution. No `Done when` point is met and nothing was merged: pull request 31
(branch `ci-os-matrix`, three commits of CI configuration) was closed and its branch deleted. `main` is unchanged; Linux x86-64
stays the only released platform (`README.md`).

What the runs of 08-11-2026 showed, for whoever takes it up (a runner of GitHub is the only test machine):

- Windows, `bin/ci` stages: each stage launches `./gradlew` with `ProcessBuilder`, which fails with `CreateProcess error=193`
  (a shell script is not a program on Windows). `bin/ci` needs a launcher of `gradlew.bat` there, and `sh` for the scripts of
  `bin/ci.d` (the `release-install` stage).
- Windows, Gradle run directly from bash: the toolchain of Java 25 was not found because the step that gives the GraalVM to
  Gradle wrote `$GRAALVM_HOME` with backslashes into `~/.gradle/gradle.properties`, where they are escapes; with the path
  converted by `cygpath -m`, `./gradlew assemble` passed. `check` was then running for more than 40 minutes with no output
  readable until the job ended (no per-step timeout at the time); the native image on Windows was never reached.
- macOS (Apple Silicon): the environment action works after one fix (BSD `sed` has no `\|`: `sed -nE`), `build` passes in about
  1.5 minutes. In `test`, every cli test that talks to a scripted daemon fails with exit code 1 (internal error) where 2 or 0
  is expected (75 of 75 in `DaemonAnswersTest`, about 278 in the module, then the stage hangs); the message the cli printed
  was not read (the run that would have printed it was cancelled). Candidates: the Unix socket of the tests, the fake daemons
  (`ScriptedDaemon`, `StartingJdtlsDaemon`), `GABIER_SOCKET`. The native image on macOS was never reached.
- Not looked at: the Python launcher of jdtls on Windows (`jdtls.bat`), the mode and line endings of files written by `init-agent`
  and `gabier create`, `scripts/*.sh` (packaging, `install.sh`), the `postinstall` that downloads jdtls.

Cost of the attempt: a runner-hour per run lost to a hang (a step with no timeout stays 60 minutes); give every step a timeout
first.

Tokens:
- worker: none (done by the supervisor)
- reviewer: none (nothing merged)
- supervisor: 81 calls, 51,239 output, 1,171,048 input + cache write (cache read 13,626,634), from 16:40 UTC to 22:00 UTC
  of 08-11-2026; covers the supervisor session over the period, other work included; exchanges with the user: 4 messages.
