# GitHub runs the full bin/ci on every pull request

Opened: 02-11-2026 20:41

The project goes to `github.com/NolwennD/gabier` (decision of the user, 02-11-2026): the first push of `main` is direct,
then the code reaches `main` through pull requests, `main` is protected, and the full `bin/ci` is a required check of
every pull request (decision of the user). A GitHub Actions workflow installs what `bin/ci` needs as the setup of
`AGENTS.md` says (the JDK of `.sdkmanrc`, the GraalVM of the same release for the native stage, the tools of `mise.toml`
with the pinned jdtls and its checksum) and runs `bin/ci --all`, so that no stage skips itself on the runner.

Done when:
- a pull request runs the workflow, every stage of `bin/ci` runs and passes, and a red stage fails the check with its
  output in the log;
- the versions come from `.sdkmanrc` and `mise.toml`, not written a second time in the workflow;
- the name of the check is the one the protection of `main` will require, written in the `## Resolution`.

Depends on: rename-the-base-package.

## Resolution

Done in `740f0d0`: `.github/workflows/ci.yml`, workflow `CI`, one job **`bin-ci`**: the name of the check that the
protection of `main` requires. It runs on `pull_request` (and `workflow_dispatch`), not on push, with read-only
permissions, one run per ref (the older is cancelled) and a 60-minute timeout, on `ubuntu-24.04`: GraalVM of the major
read from `JavaLanguageVersion.of(N)` in `build-logic`, given to the Gradle toolchains; the JDK of `.sdkmanrc` through
SDKMAN! (it runs Gradle, `bin/ci` and the jdtls launcher); mise, `mise install` and `mise run jdtls:install` (the
pinned jdtls and its sha256 from `mise.toml`); then `mise exec -- bin/ci --all`, so that no stage skips itself. No
version is written in the workflow; the three actions are pinned to a full commit SHA. mise itself is the latest release.

Proven by the run of pull request #1 (run 41517738551, 11m34s): format passed in 9s, build 99s, test 146s, native 116s,
jdtls 270s, none skipped. Locally: `actionlint` silent, `bin/ci --fast` green.

The history was rewritten before this pull request (02-11-2026, decision of the user: no Claude attribution in the
commits) and the repository recreated; the branch was rebased on the rewritten `main` by the supervisor.

Not reviewed: small ticket, a build file and no production code (`.claude/skills/ship/SKILL.md`, step 1).

Left to the user: `.sdkmanrc` pins JDK 27 while the modules build with release 25 (it works: the JDK 27 runs the tools
only).

Tokens:
- worker: 87 741
- reviewer: none (not reviewed)
- supervisor: 55 calls, 36 977 output, 140 861 input + cache write (14 813 469 cache read apart); the supervisor session from the launch of the worker to the close, the other work of the period included (the history rewrite, the attribution guard, the README); exchanges with the user: 5 messages (a floor)
