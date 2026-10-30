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
Usage error: Unsupported arguments; usage: gabier [--json] (--version | status | stop | diagnostics <file>) (usage-error; arguments=--help)
$ gabier --json --help
{"type":"urn:gabier:error:usage-error","title":"Usage error","detail":"Unsupported arguments; usage: gabier [--json] (--version | status | stop | diagnostics <file>)","key":"usage-error","parameters":{"arguments":"--help"}}
```

The JSON object follows the data model of [RFC 9457](https://www.rfc-editor.org/rfc/rfc9457) (problem details)
without its HTTP side: `type` (a URI that is not meant to be resolved, `urn:gabier:error:<key>`), `title`, `detail`,
and the extensions `key` and `parameters` (the machine-readable cause, as strings). It has no `status` member and
no `application/problem+json` media type. `title` and `detail` are English text. An error that gabier does not
know is reported as the generic `internal-server-error`, without its message, its cause or a stack trace.

### The daemon

One daemon runs per user. It is started by hand, with `gabier-daemon` from the installed distribution
(`./gradlew :daemon:installDist`, then `modules/daemon/build/install/gabier-daemon/bin/gabier-daemon`), and runs until
`gabier stop` or a SIGTERM; it answers `status`, `stop` and `diagnostics`, and nothing else yet. The cli starting it on
demand comes later.

It serves **one project**: `gabier-daemon [<project folder>]`, else the folder named by `GABIER_PROJECT`, else the
directory it is started in (a relative folder is relative to that directory; one that is not a folder is a usage error).
Starting the daemon starts nothing else. jdtls (the launcher `GABIER_JDTLS` names, as for the probe) is started by the
first request that needs it, on that folder, and the request waits until it has imported the project: the first
`diagnostics` takes seconds for a small project and about a minute and a half for this checkout. Requests that arrive
meanwhile wait for the same start, never for another. jdtls then stays up for the next requests, and is ended, with every
process it started, by `gabier stop`, by SIGTERM, and when the daemon is stopped while jdtls is still starting. A jdtls
that does not start within **5 minutes** (the time of the whole start, then of each request to it), whose launcher is
missing or that exits is not a hang: the request gets the problem object `jdtls-start-failed` (exit code 1), whose
message says why, and the next request tries again. A launcher that exits at once is reported with its exit code. A
request that waits while the daemon stops is told that the daemon is stopping: its answer is written before its
connection ends (the daemon gives the clients 5 seconds to end, then cuts them). What jdtls says of itself goes to the
standard error of the daemon, prefixed with `jdtls:`.

jdtls is ready when it says so itself: its `ServiceReady` status comes once its import is over, and the daemon then asks
the list of projects it imported, once. A folder with nothing jdtls can import (no Gradle, Maven or Eclipse build) is
refused at once, not after five minutes, with the problem object `no-build-to-import` (exit code 2). A jdtls that dies
while it is ready (killed, crashed) is seen as dead: `status` says `failed`, its exit code is on the standard error of
the daemon, and the next request ends what is left of it and starts another.

`gabier status` says the project and where jdtls is: `not-started`, `starting`, `ready` or `failed` (the last start
failed or jdtls died, until a request starts it again). When jdtls is `failed` and the reason is known, the text adds it
in parentheses (`jdtls failed (jdtls is not running any more: it exited with code 137 after it was ready)`), with
control characters written as escapes like in the text of `gabier diagnostics`, and `--json` has it exactly, as the
member `jdtlsFailure`, which is absent otherwise. It never starts jdtls.

```text
$ gabier status
gabier-daemon 0.1, pid 4242, up 65s, project /work/demo, jdtls not-started
$ gabier --json status
{"version":"0.1","pid":4242,"uptimeMillis":65432,"project":"/work/demo","jdtls":"not-started"}
$ gabier stop
gabier-daemon stopped
$ gabier status
Daemon not running: No daemon answers on /run/user/1000/gabier/gabier.sock: start it with gabier-daemon (daemon-not-running; socket=/run/user/1000/gabier/gabier.sock)
```

`gabier diagnostics <file>` asks what the Java language server reports in a file, instead of building: one line per
problem, `file:line:column: severity: message`, the file as typed and the line and column **from 1**, as an editor and a
compiler show them (severity: `error`, `warning`, `information` or `hint`), and nothing when the file is clean. The exit
code is 0 whenever the daemon answered, whatever the problems: the reader reads the list. With `--json`, the result is
`{"diagnostics":[{"severity":"error","line":4,"column":15,"message":"..."}]}`, an empty list for a clean file, with the
positions **from 0** as the protocol and the domain count them. A refusal (the file is outside the project, the server
is not ready) is the problem object, exit code 2. The cli sends the absolute path of the file to the daemon, as typed otherwise, and the daemon alone cleans it: `.` and
`..` are collapsed **lexically** and no symbolic link is followed. So with `src` a link to `/other/src`,
`gabier diagnostics src/../Foo.java` asks for `./Foo.java`, where a shell would open `/other/Foo.java`; type the path
you mean. This is chosen: the folders of the projects are matched as paths, not as files, so resolving the links in the
cli would make a file of a project reached through a link (a `~/work` that links to another disk) look outside it. In
text, the file and the message are shown with the characters a terminal obeys or does not show written as `\uXXXX`
escapes (`\u{XXXXX}` beyond the basic plane) and each backslash doubled, so that a message quoting source text cannot
drive the terminal, fake a line or hide behind a blank. Escaped: the control characters (C0, DEL, C1), the line and
paragraph separators (U+2028, U+2029), the format characters (bidirectional controls, zero-width characters, tag
characters), every space separator but the space (the no-break space U+00A0, U+1680, U+2000 to U+200A, U+202F, U+205F,
U+3000), the Hangul fillers (U+115F, U+1160, U+3164, U+FFA0), the braille blank U+2800, the combining grapheme joiner
U+034F, the variation selectors (U+FE00 to U+FE0F, U+E0100 to U+E01EF) and an unpaired surrogate. Kept as they are: the
space (U+0020), and every other character, such as an accent, an ideograph or an emoji. The same goes for every text
the cli prints from what the daemon or the user gave: the problem of a refusal, and the whole line of `gabier status`.
With `--json` the text is the exact one, and JSON escapes only what it must plus DEL and U+0080 to U+009F (written
`\u007f`, `\u009b`...), so that the C0, DEL and C1 controls of the JSON do not drive a terminal (the other characters above are written as they are in `--json`). The daemon serves the project it was started
for (see above).

```text
$ gabier diagnostics src/main/java/demo/Broken.java
src/main/java/demo/Broken.java:5:16: error: Type mismatch: cannot convert from String to int
$ gabier --json diagnostics src/main/java/demo/Main.java
{"diagnostics":[]}
```

`gabier stop` returns once the daemon has removed its socket and hung up. With no daemon, or with a socket file that
nobody answers on, `status` and `stop` do not wait: they report `daemon-not-running` at once, with exit code 2.

The cli and the daemon talk JSON-RPC 2.0 on a Unix domain socket, one JSON object per line (no framing header). A
request is `{"jsonrpc":"2.0","id":1,"method":"status"}`. A request may carry `params`, an object whose members are
strings (`"params":{"file":"src/A.java"}`): a method refuses, as a bad request, a parameter it does not take
(`unknown-parameter`) and one it needs and lacks (`missing-parameter`), the name of the parameter in the `parameters` of
the problem, and a value it cannot read (`invalid-parameter`). A request that carries both an unknown and a missing
parameter is refused for the unknown one, the first by name when there are several; with only missing ones, the first
by name is named. A `params` that is not an object (an array, a string, a number, `null`) and a member of `params` that
is not a string are refused with the key `invalid-request` and the name of the offender in `parameter` (`params` or the
member). All of these are answered with the code `-32602` ("Invalid params"), the one a problem object that names a
`parameter` gets. A request without an `id` (a notification) and a batch are refused as invalid requests. An error is a
JSON-RPC error whose `message` is the title of the problem and whose `data` is the problem object: its `code` tells who
is at fault (`-32603` a failure of gabier, `-32602` a parameter refused, `-32700` a line that is not JSON, `-32600` not a
request, `-32601` unknown method, any other code, `-32000`, a refusal of the request).

**A key that is repeated in an object is refused**, at any depth of the request (`{"file":1,"file":"A.java"}`,
`{"id":1,"id":2,...}`, the same name written with and without a `\u` escape): the line is not read as a request, it is
answered with `-32700` (`malformed-json`, at the position of the second occurrence, without quoting the name) and an
`id` of `null`. This is the choice that cannot be misread: a request whose first `file` the sender meant, and the last
one the daemon read, is never run (RFC 8259 leaves it open and I-JSON, RFC 7493, forbids it). The same parser reads
the answers of the daemon. A parameter name or value with control characters is written escaped in the answer, which
stays on one line. A malformed line, an unknown method or a client that disconnects in the middle of a request leaves
the daemon running.

A line is read up to 1 MiB (1,048,576 bytes, its line break not counted); a longer one is refused at once as a bad
request (`line-too-long`, the limit in the `limit` of the problem, `id` null), the rest of it is dropped, and the
connection goes on with the next line. This limit is the only bound on the size of a parameter name or value: a request
whose `params` hold a name or a value that does not fit in the line is refused for its length, before it is read, and
there is no second, smaller bound.

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
