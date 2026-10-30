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
gabier (a usage error: any other argument, or none; no daemon running...) is reported as a **problem object** on the
standard error, never on the standard output: one line of text by default, one line of JSON with `--json` (the option
can come anywhere on the command line).

```text
$ gabier --help
Usage error: Unsupported arguments; usage: gabier [--json] (--version | status | stop) (usage-error; arguments=--help)
$ gabier --json --help
{"type":"urn:gabier:error:usage-error","title":"Usage error","detail":"Unsupported arguments; usage: gabier [--json] (--version | status | stop)","key":"usage-error","parameters":{"arguments":"--help"}}
```

The JSON object follows the data model of [RFC 9457](https://www.rfc-editor.org/rfc/rfc9457) (problem details)
without its HTTP side: `type` (a URI that is not meant to be resolved, `urn:gabier:error:<key>`), `title`, `detail`,
and the extensions `key` and `parameters` (the machine-readable cause, as strings). It has no `status` member and
no `application/problem+json` media type. `title` and `detail` are English text. An error that gabier does not
know is reported as the generic `internal-server-error`, without its message, its cause or a stack trace.

### The daemon

One daemon runs per user. It is started by hand, with `gabier-daemon` from the installed distribution
(`./gradlew :daemon:installDist`, then `modules/daemon/build/install/gabier-daemon/bin/gabier-daemon`), and runs until
`gabier stop` or a SIGTERM; it answers `status` and `stop`, and nothing else yet. The cli starting it on demand comes
later.

```text
$ gabier status
gabier-daemon 0.1, pid 4242, up 65s
$ gabier --json status
{"version":"0.1","pid":4242,"uptimeMillis":65432}
$ gabier stop
gabier-daemon stopped
$ gabier status
Daemon not running: No daemon answers on /run/user/1000/gabier/gabier.sock: start it with gabier-daemon (daemon-not-running; socket=/run/user/1000/gabier/gabier.sock)
```

`gabier stop` returns once the daemon has removed its socket and hung up. With no daemon, or with a socket file that
nobody answers on, `status` and `stop` do not wait: they report `daemon-not-running` at once, with exit code 2.

The cli and the daemon talk JSON-RPC 2.0 on a Unix domain socket, one JSON object per line (no framing header). A
request is `{"jsonrpc":"2.0","id":1,"method":"status"}`. A request may carry `params`, an object whose members are
strings (`"params":{"file":"src/A.java"}`): a method refuses, as a bad request, a parameter it does not take
(`unknown-parameter`) and one it needs and lacks (`missing-parameter`), the name of the parameter in the `parameters` of
the problem; a `params` that is not an object of strings is an invalid request. A request without an `id` (a
notification) and a batch are refused as invalid requests. An error is a JSON-RPC error whose `message` is the title of
the problem and whose `data` is the problem object: its `code` tells who is at fault (`-32603` a failure of gabier, any
other code a refusal of the request, `-32700` a line that is not JSON, `-32600` not a request, `-32601` unknown method).
A malformed line, an unknown method or a client that disconnects in the middle of a request leaves the daemon running.

**The socket** is per user, in a short place, because a Unix socket path holds at most 107 bytes (`sun_path` is 108,
one of them for the terminating zero) and a path under a checkout or a worktree easily exceeds it:
`$XDG_RUNTIME_DIR/gabier/gabier.sock` (`/run/user/<uid>`), else `/tmp/gabier-<uid>/gabier.sock`. `GABIER_SOCKET` replaces
the path, for the daemon and for the cli alike. A path over 107 bytes (counted in UTF-8: a multi-byte name counts for
its bytes) is refused before anything is bound, with the `socket-path-too-long` problem, which gives the path, its
length, the limit and the variable to set. The folder of the socket must be the user's own and private to them
(mode 700; it is created so, and an existing one that is not is refused, never changed), and the socket file is made
`rw-------`. A daemon refuses to start while another one answers on the socket (`daemon-already-running`), removes a
socket file that nobody answers on (what a killed daemon leaves) but nothing else: a file that is not a socket of the
user's own is never removed.

### Exit codes

The exit code is derived from the problem. These codes are stable, for `gabier` and for `gabier-daemon`.

| Code | Meaning                                                                                                   |
| ---- | --------------------------------------------------------------------------------------------------------- |
| 0    | Success.                                                                                                  |
| 1    | Internal error: gabier failed (a bug, or the environment), the request was not at fault.                  |
| 2    | Request refused: a usage error, no daemon running, a socket path too long, any refusal of a request.      |

## License

gabier is released under the [Apache License 2.0](LICENSE).
