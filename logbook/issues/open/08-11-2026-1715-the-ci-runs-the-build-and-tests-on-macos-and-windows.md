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
