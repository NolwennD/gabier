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
