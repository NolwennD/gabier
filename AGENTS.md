# Working on gabier

Instructions for an agent (or a person) who changes this repository. What gabier is and what it does is in the
[README](README.md).

## Setup

The tools (hk, open-java-format, ripwire, jdtls) are pinned in `mise.toml`. One command installs them and the Git
pre-commit hook:

```sh
mise install
```

In a new Git worktree, run `mise trust` once first (mise trusts a configuration per path).

If the hook is missing (for example `MISE_NO_HOOKS` is set), install it by hand: `hk install --mise`, and for jdtls
`mise run jdtls:install`.

`mise install` also installs the pinned jdtls into `~/.local/share/jdtls/<version>`, shared by all worktrees
(`mise run jdtls:install` does it by hand); mise exports the path of its launcher as `GABIER_JDTLS`, which the integration
tests (`./gradlew jdtlsProbe`) read. In a shell where mise is not activated, run commands through `mise exec --` to get
`GABIER_JDTLS` and the pinned tools (`jdtls`, `ripwire`, `hk`) on the `PATH`.

The launcher is a Python 3 script (the project itself uses no Python) and needs a JDK 21 or later: it runs
`$JAVA_HOME/bin/java` if that file exists, otherwise the `java` found on the `PATH`, and fails under an older JDK.

`mise run install` puts `gabier` on the `PATH` for the agents of the machine: it builds the native cli and the daemon of
the checkout, **copies** them into `~/.local/share/gabier/<version>` (`GABIER_INSTALL_DIR` to change it) and links
`gabier` and `gabier-daemon` from `~/.local/bin` to that copy (`GABIER_BIN_DIR`). The install does not depend on the
checkout (`./gradlew clean` or another branch leaves it working), the cli starts the daemon of its own copy, and a new
install is what takes a new build ([the daemon](docs/daemon.md)).

The JDK of the project is the one `.sdkmanrc` pins, for [SDKMAN!](https://sdkman.io). With `sdkman_auto_env=true` in
`~/.sdkman/etc/config`, entering the directory selects it (`sdk env install` once to fetch it, `sdk env` to select it by
hand). The native cli also needs a GraalVM of the same release, which Gradle finds among the SDKMAN! candidates.

The pre-commit hook formats the staged `.java` files with
[open-java-format](https://github.com/openjavaformat/open-java-format) (one style, 120 columns), as
configured in `hk.pkl`. Never bypass it with `--no-verify`.

To format or check every Java file by hand: `hk fix --all` and `hk check --all`.

## Rules and skills

The way of working, the quality gates, the logbook of issues and the use of gabier on gabier are in `.claude/rules/`
(`workflow.md`, `quality.md`, `logbook.md`, `dogfooding.md`); the procedures (`ticket`, `ship`, `verify`,
`add-daemon-method`, `add-scenario`) are in `.claude/skills/`. Read them before changing code: they are not repeated here.

## Releasing

Pushing a tag `v<semantic version>` (`v0.1.0-rc.1`, then `v0.1.0`) runs `.github/workflows/release.yml`. It runs the
full `bin/ci` on the tagged commit (the check of the pull requests), builds with the version of the tag, checks that the
cli and the daemon print it, and attaches the assets to the release of the tag (the cli, the daemon archive, the archive
of mise, `install.sh` and `SHA256SUMS`), with the notes of `.github/release-notes.md`.

- **What changed in the commands is written before the tag.** Each version has a file `docs/releases/<version>.md`
  (the version is the tag without its `v`, whole: `0.6.0-rc.1` has `docs/releases/0.6.0-rc.1.md`, not the file of
  `0.6.0`), a section `## New in <version>` written from what a user of the commands sees between the previous tag and
  this one (`git log <previous tag>..HEAD`, the issues closed in that range, `docs/commands`): a new command, a changed
  option, output or refusal, a changed requirement, and `No change to the commands.` when it is true. It is merged to
  `main` with the work, before the tag is pushed. `scripts/release-notes.sh` puts it in the notes of the release, in the
  place of the line `@WHAT_CHANGED@` of `.github/release-notes.md` (the template stays generic), and the release
  workflow fails, in its first job and before anything is built or published, when the file of the tag is missing or
  blank, with a message that names it. The stage `release-notes` of `bin/ci` checks the script without a tag; it asks
  nothing of the versions released before the rule (`docs/releases/` holds `0.5.0.md` only). A published release is not
  edited to match.
- A version with a pre-release suffix (`-rc.1`, any `-...`) gives a release marked pre-release, and the programs print
  the whole version (`0.1.0-rc.1`). A version with no suffix gives a normal release.
- The version that `gabier --version` and `gabier status` print is the Gradle property `gabierVersion`
  (`-PgabierVersion=0.1.0`), written in a resource of the `protocol` module at build time. A build made with no such
  property, such as `mise run install`, is `0.0.0-dev`.
- `scripts/package-release.sh <version> <folder>` makes the assets from a build made with that property. It writes the
  `install.sh` of the release from `scripts/release-install.sh.template`, filling the version and the jdtls pins of
  `mise.toml` (the placeholders `@VERSION@`, `@JDTLS_VERSION@`, `@JDTLS_SHA256@`, as in the release notes). Change the
  template, not a release's file; a bump of the jdtls pin reaches the next release by itself.
- The archive of mise, `gabier-linux-x86_64.tar.gz`, is what `mise use github:NolwennD/gabier@<version>` installs: `bin/gabier`
  (native cli), `bin/gabier-daemon` (the start script), `lib/` (its jars) and `jdtls/` (the pinned jdtls, downloaded from
  Eclipse and checked against the sha256 of `mise.toml` by `scripts/jdtls-install.sh` while packaging). mise puts `bin/` on
  the PATH, the cli starts the daemon next to it, and the daemon finds `jdtls/bin/jdtls` above its own `lib/`
  (`JdtlsLocation`): no `postinstall` and no variable in the `mise.toml` of the user, and a change of version changes the
  three together. The name has no version and no longer matches the bare cli `gabier-linux-x86_64`, so that the
  autodetection of the github backend of mise picks the archive over it (same score, +10 for an archive): do not rename
  these two, and do not add an asset that scores as high for linux x86-64. The release also keeps the bare cli, the daemon
  archive and `install.sh`, which `scripts/release-install.sh.template` uses.
