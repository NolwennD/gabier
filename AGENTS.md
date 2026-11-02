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
cli and the daemon print it, and attaches the assets to the release of the tag, with the notes of
`.github/release-notes.md`.

- A version with a pre-release suffix (`-rc.1`, any `-...`) gives a release marked pre-release, and the programs print
  the whole version (`0.1.0-rc.1`). A version with no suffix gives a normal release.
- The version that `gabier --version` and `gabier status` print is the Gradle property `gabierVersion`
  (`-PgabierVersion=0.1.0`), written in a resource of the `protocol` module at build time. A build made with no such
  property, such as `mise run install`, is `0.0.0-dev`.
- `scripts/package-release.sh <version> <folder>` makes the assets from a build made with that property.
