# The CI keeps the tools it downloads, so a pinned version is fetched once and not at every check

Opened: 08-11-2026 11:30

Met on 08-11-2026: the check `bin-ci` of two pull requests failed six times before any test, on HTTP 504 from the
download of a tool whose version had not changed (the SDKMAN! installer, the `mise` binary, the Temurin JDK; the release
assets of github.com answered 504 by moments with no incident declared). The versions are pinned and move rarely
(`.sdkmanrc`, `mise.toml`, the Java release of `build-logic`), so a cache keyed on those files turns a download that fails
at random into one that is made once. Release builds (`release.yml`) use the same action and gain the same.

Done when:
- [x] the action restores, before the install steps, what they download and a pinned version does not change: the JDK of
      `.sdkmanrc`, the GraalVM, the tools and jdtls of `mise.toml` (and the binary of mise if the action can keep it),
      each cache keyed on the files that pin it (a bump of a pin changes the key; no version is written a second time);
- [x] a cache miss still installs as today; a hit shows in the log that nothing was downloaded; the cache is saved by
      `main` too (a cache saved on a pull request branch is not seen by another one), by a run on a push to `main` or the
      mechanism that fits, so the next pull request starts with a hit;
- [x] shown on two runs of the same commit (the first fills, the second hits) with the time of each; the comments of the
      action say what is cached and why; `bin-ci` is green on the pull request of the change.

Depends on: the-ci-installs-its-jdk-without-sdkman (same file, `.github/actions/setup-build/action.yml`).

## Resolution

Done in f9db913, 5efcdee and 04ea0c3 (pull requests 23 and 24). The first version had two defects that only the runs of
GitHub could show, so there are three commits for this ticket and not one: Gradle's own downloads were not cached (the
distribution timed out in run 42471945402), and `mise-action` saves its cache only when it installs the tools itself
(`install: true`), which the first version turned off, so the cache of mise was never written (run 42473971194).

- `setup-build/action.yml` reads the pins (Java release of `build-logic`, `.sdkmanrc`, jdtls version and sha256 of
  `mise.toml`) and restores, before the setup actions, one `actions/cache` for the GraalVM and the Temurin of the tool
  cache and for jdtls, keyed on those pins. `setup-java` keeps the Gradle distribution and dependencies (`cache:
  gradle`). `mise-action` keeps the binary and the tools of `mise.toml`, and installs them itself.
- `.github/workflows/warm-cache.yml` runs on a push to `main` that touches a pin, every Monday, and by hand: it installs
  the environment and runs `bin/ci --fast`, so that `main` holds the caches (a cache saved by a pull request is seen
  by that request only). Pull requests restore from `main`.
- Shown: the first warm-up on `main` (run 42476380325, 3 min 33 s) found no cache and saved them; the second, same commit
  (run 42476703765, 4 min 8 s), hit the four ("Cache hit for: tools-…", the two `setup-java-…-gradle` keys,
  "mise cache restored", "installed 0 tools · 3 already installed"). The second run was not shorter: GitHub
  had recovered by then and the time is dominated by `bin/ci --fast`. What the cache removes is the dependency on the
  downloads, which took 6 minutes in the run 42474479013 (the tools of `mise` through a degraded GitHub) and failed in six
  others. `bin-ci` green on the pull requests 23 and 24, every stage.
- Choices: the version of mise is now written in the action (`version: 2026.11.4`), because its cache is keyed on it and
  an unpinned version makes a new key at each release of mise; a bump is a change of that line. `Bypassed` pushes: none.
  A new tool in `mise.toml` is a miss of the cache of mise only; a change of the Java release, of `.sdkmanrc` or of the
  jdtls pin changes the key of the first cache.

Tokens:
- worker: none (the supervisor did this ticket itself, at the user's request: a workflow file that only the GitHub check can prove).
- reviewer: none (not reviewed: small ticket, workflow files only, no production code).
- supervisor: 136 calls, 88601 output tokens, 181907 input + cache write (21385301 cache reads, apart), from 11:09 to 12:44 on 08-11-2026
  (10:09 to 11:44 UTC) for this ticket and `the-ci-caches-the-tools-it-downloads`, with the review and the waiting for the checks of the two
  code pull requests of the day in the same period; exchanges with the user: 4 messages.
