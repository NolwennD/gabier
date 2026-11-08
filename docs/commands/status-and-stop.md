# gabier status and gabier stop

`gabier status` says the project and where jdtls is: `not-started` (no request has needed it yet), `starting`, `ready` or
`failed` (the last start failed or jdtls died, until a request starts it again).

**While jdtls is starting**, `status` also says what it is doing and for how long it has been doing it, so that an agent
knows how far the first call is and that it waits for a normal reason. The daemon keeps the last `language/status` that
jdtls sent with the type `Starting` (it needs no setting of the client) and tells two stages, in order:

- `launching`: the process is started and jdtls has not begun to import the project (its JVM and its runtime come up,
  about 6 seconds); the message is the last status it sent, such as `Init...`;
- `importing`: jdtls imports the project; for a Gradle project it starts a Gradle daemon and runs the build, which is most
  of the wait (about 54 seconds for this repository), and the message is the task it reported
  (`Configure project :core`, `Task :build-logic:compileKotlin`).

The time is the time in the stage, counted from the first status that put jdtls in it. The numbers jdtls writes in front
of its statuses (`70%`, and even `1800%`) are not a percentage and are not shown. The message is jdtls's own text, so
the text form writes the control characters out as escapes, like `gabier diagnostics` does. A `ready`, `not-started` or
`failed` jdtls says nothing more than before. In the text form the stage follows the state in parentheses; with `--json`
it is the member `jdtlsProgress` (`phase`, `message`, `spentMillis`), present only while jdtls is starting. The same
stage is in the line a command writes on the standard error while it waits for the start, see [the
daemon](../daemon.md). When jdtls is `failed` and the reason is known, the text adds it
in parentheses (`jdtls failed (jdtls is not running any more: it exited with code 137 after it was ready)`), with
control characters written as escapes like in the text of `gabier diagnostics`, and `--json` has it exactly, as the
member `jdtlsFailure`, which is absent otherwise. It never starts jdtls.

It also shows **the import settings the project follows** (see [organize-imports](organize-imports.md)): for each of the
three settings of jdtls that decide what `organize-imports` writes, its value and where it comes from, `.settings`
(`.settings/org.eclipse.jdt.ui.prefs`), `.vscode` (`.vscode/settings.json`) or `default of jdtls` when no file sets it.
The files are read when `status` is asked, not when the daemon started, so a file edited a moment ago is already in it;
with `--json` they are the member `imports`, each setting an object with its `value` and its `source`.

```text
$ gabier status
gabier-daemon 0.1.0, pid 4242, up 65s, project /work/demo, jdtls not-started
imports:
  java.completion.importOrder = ["#", ""] (.vscode)
  java.sources.organizeImports.starThreshold = 2147483647 (.vscode)
  java.sources.organizeImports.staticStarThreshold = 99 (default of jdtls)
$ gabier --json status
{"version":"0.1.0","pid":4242,"uptimeMillis":65432,"project":"/work/demo","jdtls":"not-started","imports":{"importOrder":{"value":["#",""],"source":".vscode"},"starThreshold":{"value":2147483647,"source":".vscode"},"staticStarThreshold":{"value":99,"source":"default"}}}
$ gabier status    # a first command is waiting for jdtls to import the project
gabier-daemon 0.1.0, pid 4242, up 52s, project /work/demo, jdtls starting (importing the project for 41s: Configure project :core)
imports:
  java.completion.importOrder = ["#", ""] (.vscode)
  java.sources.organizeImports.starThreshold = 2147483647 (.vscode)
  java.sources.organizeImports.staticStarThreshold = 99 (default of jdtls)
$ gabier stop
gabier-daemon stopped
$ gabier status
Daemon not running: No daemon answers on /run/user/1000/gabier/demo-6f1c0d4e.sock: start it with gabier-daemon (daemon-not-running; socket=/run/user/1000/gabier/demo-6f1c0d4e.sock)
```

`gabier stop` returns once the daemon has removed its socket and hung up. With no daemon, or with a socket file that
nobody answers on, `status` and `stop` do not wait and do not start one: they report `daemon-not-running` at once, with
exit code 2.

## Refusals

A refusal is the problem object on the standard error, exit code 2. `gabier status` and `gabier stop` answer the same
keys, and the help of each (`gabier help status`, `gabier help stop`) lists them:

- `daemon-not-running`: no daemon answers; the command never starts one.
- `usage-error`: the arguments are not those of the command.
- `daemon-connection-failed`: the connection to the daemon was lost, or it hung up without answering.
