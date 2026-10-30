# gabier

## Setup

The tools (hk, open-java-format) are pinned in `mise.toml`. One command installs them and the Git
pre-commit hook:

```sh
mise install
```

In a new Git worktree, run `mise trust` once first (mise trusts a configuration per path).

If the hook is missing (for example `MISE_NO_HOOKS` is set), install it by hand: `hk install --mise`.

The pre-commit hook formats the staged `.java` files with
[open-java-format](https://github.com/openjavaformat/open-java-format) (one style, 120 columns), as
configured in `hk.pkl`. Never bypass it with `--no-verify`.

To format or check every Java file by hand: `hk fix --all` and `hk check --all`.

## License

gabier is released under the [Apache License 2.0](LICENSE).
