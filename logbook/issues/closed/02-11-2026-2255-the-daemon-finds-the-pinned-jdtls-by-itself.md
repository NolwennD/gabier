# The daemon finds the pinned jdtls by itself

Opened: 02-11-2026 22:55

An agent can use gabier on any Java project, not only in the gabier checkout. Today only the `mise.toml` of gabier
exports `GABIER_JDTLS`, so in another project the daemon inherits no such variable and the first request fails with
`GABIER_JDTLS is not set`. When the variable is not set, the daemon falls back to the jdtls that `mise run jdtls:install`
installs, `~/.local/share/jdtls/<pinned version>/bin/jdtls` (the version of `mise.toml`, known to the build). If that
jdtls is missing, it falls back to a `jdtls` on the `PATH`. `GABIER_JDTLS` still wins when it is set.

Done when:
- A request run in a project that has no `mise.toml`, with `GABIER_JDTLS` unset and the pinned jdtls installed, is
  answered.
- With `GABIER_JDTLS` set, its launcher is the one started, whatever is installed.
- With no variable, no pinned jdtls and no `jdtls` on the `PATH`, the refusal names the three places looked at and
  `mise run jdtls:install`.
- The pinned version that the daemon uses comes from `mise.toml` at build time, never from a second copy of the number.
- `docs/daemon.md` and the README say where the daemon looks for jdtls.

## Resolution

Fixed in `4d4d0af` (pull request #5). `JdtlsLocation` (daemon) resolves the launcher at every jdtls start, in this order:
1. `GABIER_JDTLS`, taken as it is (a blank value counts as unset);
2. `~/.local/share/jdtls/<pinned version>/bin/jdtls`, if it can be run;
3. a `jdtls` of the `PATH` that is a file and can be run.

With none of the three, the refusal `no jdtls found` names the three places and `mise run jdtls:install`.

The pinned version is read from `jdtls_version` in `mise.toml` by `modules/daemon/build.gradle.kts`, which writes it into
`jdtls.properties` the way `version.properties` is filled in `protocol`. It is an input of `processResources`, a line
of another form fails the build, and the test `Pinned.isTheVersionOfMiseToml` compares the two. `Main.run` and
`ProjectServer.jdtls` take the home folder, so the tests use a temporary one. `docs/daemon.md` has a new section, "Where
the daemon finds jdtls", and the README and the `verify` skill are updated.

Checked by the worker (`bin/ci`): format passed in 18s, build in 102s, test in 299s, native in 191s, jdtls in 497s.
- The supervisor rebased the branch over `main`, which had the install ticket (`2c75dfa`).
- The rebase had one conflict, in the text of the README: both paragraphs are kept.
- `bin-ci` on GitHub passed on the rebased commit, in 11m45s.

Proof of each `Done when` point:
- A daemon with no variable in a project with no `mise.toml`: `InstalledDaemonTest.startsThePinnedJdtlsInstalledUnderTheHomeFolderWhenGabierJdtlsIsNotSet`
  (the installed daemon, a fake home, `PATH=/usr/bin:/bin`, `diagnostics` on the real jdtls).
- The variable wins: `JdtlsLocationTest.Finding.takesTheLauncherTheVariableNamesWhateverIsInstalled` and
  `ProjectServerTest.OfJdtls.startsTheLauncherOfTheVariableWhateverIsInstalled`.
- The refusal: `JdtlsLocationTest.Refusing.*`.
- The version from `mise.toml`: `JdtlsLocationTest.Pinned.*`.
- The docs: `docs/daemon.md` and the README.

Review (once, no blocking finding):
- The daemon takes `user.home`, which the JVM reads from the passwd entry, while mise installs under `$HOME`. This one
  goes to `02-11-2026-2324-the-jdtls-lookup-follows-home.md`, on hold.
- `skipsTheEmptyEntriesOfThePath` cannot fail. It goes to the same issue.
- A comment of `InstalledDaemonTest` is wrapped badly. The supervisor's follow-up fixes it.

Tokens:
- worker: 136694
- reviewer: 63376
- supervisor: 83 calls, 56240 output, 112352 input + cache write (10682919 cache reads, apart). This covers the
  supervisor session from the launch of the two workers of this period, so the install ticket, the zsh pass and its
  pull request are included. Exchanges with the user: 1 message.
