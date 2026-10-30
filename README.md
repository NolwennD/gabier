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
(`./gradlew jdtlsProbe`) reads. In a shell where mise is not activated, run commands through `mise exec --` to get
`GABIER_JDTLS` and `jdtls` on the `PATH`.

The launcher needs Python 3 and a JDK 21 or later: it runs `$JAVA_HOME/bin/java` if that file exists, otherwise the
`java` found on the `PATH`, and fails under an older JDK. The repository's `.sdkmanrc` selects JDK 27.

The pre-commit hook formats the staged `.java` files with
[open-java-format](https://github.com/openjavaformat/open-java-format) (one style, 120 columns), as
configured in `hk.pkl`. Never bypass it with `--no-verify`.

To format or check every Java file by hand: `hk fix --all` and `hk check --all`.

## Command line

`gabier --version` prints `gabier <protocol version>` on the standard output and exits with 0. Every refusal of
gabier (for now the usage error: any other argument, or none) is reported as a **problem object** on the standard
error, never on the standard output: one line of text by default, one line of JSON with `--json` (the option can
come anywhere on the command line).

```text
$ gabier --help
Usage error: Unsupported arguments; usage: gabier [--json] --version (usage-error; arguments=--help)
$ gabier --json --help
{"type":"urn:gabier:error:usage-error","title":"Usage error","detail":"Unsupported arguments; usage: gabier [--json] --version","key":"usage-error","parameters":{"arguments":"--help"}}
```

The JSON object follows the data model of [RFC 9457](https://www.rfc-editor.org/rfc/rfc9457) (problem details)
without its HTTP side: `type` (a URI that is not meant to be resolved, `urn:gabier:error:<key>`), `title`, `detail`,
and the extensions `key` and `parameters` (the machine-readable cause, as strings). It has no `status` member and
no `application/problem+json` media type. `title` and `detail` are English text. An error that gabier does not
know is reported as the generic `internal-server-error`, without its message, its cause or a stack trace.

### Exit codes

The exit code is derived from the problem. These codes are stable.

| Code | Meaning                                                                                     |
| ---- | ------------------------------------------------------------------------------------------- |
| 0    | Success.                                                                                    |
| 1    | Internal error: gabier failed (a bug, or the environment), the request was not at fault.   |
| 2    | Request refused: the usage error, and later any refusal of a request (invalid position...). |

## License

gabier is released under the [Apache License 2.0](LICENSE).
