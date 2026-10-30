# gabier

## Setup

The tools (hk, open-java-format, jdtls) are pinned in `mise.toml`. One command installs them and the Git
pre-commit hook:

```sh
mise install
```

In a new Git worktree, run `mise trust` once first (mise trusts a configuration per path).

If the hook is missing (for example `MISE_NO_HOOKS` is set), install it by hand: `hk install --mise`, and for jdtls
`mise run jdtls:install`.

`mise install` also installs the pinned jdtls into `~/.local/share/jdtls/<version>`, shared by all worktrees
(`mise run jdtls:install` does it by hand); mise exports the path of its launcher as `GABIER_JDTLS`, which the probe
tests read. In a shell where mise is not activated, run commands through `mise exec --` to get `GABIER_JDTLS` and
`jdtls` on the `PATH`.

The launcher needs Python 3 and a JDK 21 or later: it runs `$JAVA_HOME/bin/java` if that file exists, otherwise the
`java` found on the `PATH`, and fails under an older JDK. The repository's `.sdkmanrc` selects JDK 27.

The pre-commit hook formats the staged `.java` files with
[open-java-format](https://github.com/openjavaformat/open-java-format) (one style, 120 columns), as
configured in `hk.pkl`. Never bypass it with `--no-verify`.

To format or check every Java file by hand: `hk fix --all` and `hk check --all`.

## License

gabier is released under the [Apache License 2.0](LICENSE).
