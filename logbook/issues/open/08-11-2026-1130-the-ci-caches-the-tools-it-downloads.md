# The CI keeps the tools it downloads, so a pinned version is fetched once and not at every check

Opened: 08-11-2026 11:30

Met on 08-11-2026: the check `bin-ci` of two pull requests failed six times before any test, on HTTP 504 from the
download of a tool whose version had not changed (the SDKMAN! installer, the `mise` binary, the Temurin JDK; the release
assets of github.com answered 504 by moments with no incident declared). The versions are pinned and move rarely
(`.sdkmanrc`, `mise.toml`, the Java release of `build-logic`), so a cache keyed on those files turns a download that fails
at random into one that is made once. Release builds (`release.yml`) use the same action and gain the same.

Done when:
- [ ] the action restores, before the install steps, what they download and a pinned version does not change: the JDK of
      `.sdkmanrc`, the GraalVM, the tools and jdtls of `mise.toml` (and the binary of mise if the action can keep it),
      each cache keyed on the files that pin it (a bump of a pin changes the key; no version is written a second time);
- [ ] a cache miss still installs as today; a hit shows in the log that nothing was downloaded; the cache is saved by
      `main` too (a cache saved on a pull request branch is not seen by another one), by a run on a push to `main` or the
      mechanism that fits, so the next pull request starts with a hit;
- [ ] shown on two runs of the same commit (the first fills, the second hits) with the time of each; the comments of the
      action say what is cached and why; `bin-ci` is green on the pull request of the change.

Depends on: the-ci-installs-its-jdk-without-sdkman (same file, `.github/actions/setup-build/action.yml`).
