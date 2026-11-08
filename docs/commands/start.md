# gabier start

`gabier start` starts the daemon of the project when none runs, and jdtls in it, and answers **at once**. An agent runs
it first thing in a session, in the background, and works on something else while jdtls imports the project (about a
minute for a Gradle project, see [the daemon](../daemon.md)): its first real command then finds jdtls ready and is quick.
Without it the daemon starts at the first command, which pays the whole wait.

```text
$ gabier start
gabier-daemon started, pid 4242, project /work/demo, jdtls starting; socket /run/user/1000/gabier/demo-6f1c0d4e.sock, log /run/user/1000/gabier/demo-6f1c0d4e.sock.log
$ gabier start
gabier-daemon running, pid 4242, project /work/demo, jdtls starting; socket /run/user/1000/gabier/demo-6f1c0d4e.sock
$ gabier --json start
{"daemon":"running","version":"0.1.0","pid":4242,"project":"/work/demo","jdtls":"starting","socket":"/run/user/1000/gabier/demo-6f1c0d4e.sock"}
```

- The word after `gabier-daemon` is `started` when this call started the daemon, and only then the line names its log
  (`<socket>.log`, where a failed start leaves its reason), else `running`. With `--json`, the member `daemon` says the
  same and the member `log` is present only when it started.
- `jdtls` is the state `gabier status` shows. A daemon whose jdtls is already starting or ready starts nothing: `start`
  answers `starting` or `ready`. A jdtls that had failed is started again, as the next request would. If it fails at
  once, `start` still answers (`jdtls failed (reason)`; the member `jdtlsFailure` with `--json`).

## Waiting

`gabier start --wait [--timeout=<seconds>]` answers once jdtls is **ready**, with the same line (`jdtls ready`). Use it
when the next step needs jdtls (a script, the first step of a CI job). `--timeout` is a whole number of seconds from 1
(300 by default). It is a usage error without `--wait`.

While it waits, `start --wait` writes where jdtls is on the **standard error**, one line after 3 seconds and again every
15 seconds, the same line a command that waits for jdtls writes (see [the daemon](../daemon.md)); an answer that comes
within 3 seconds writes none, and the standard output is the answer alone, in text or with `--json`.

- When jdtls **fails**, the refusal is `jdtls-start-failed`, the key a request that waits for the same failure gets, with
  the reason when the daemon knows it. Exit code 1.
- When the time **passes**, the refusal is `jdtls-start-timeout`: it says the last state of jdtls and what it was doing
  (`jdtls was not ready after 30 seconds (starting, importing the project for 24s: Configure project :core)`), with the
  parameters `jdtls`, `progress` (when jdtls was starting), `project` and `timeoutSeconds`. jdtls goes on starting in
  the daemon: run `gabier start --wait` again, or `gabier status`. Exit code 1, as the other failures to bring the daemon
  up (`daemon-start-failed`): gabier did not do what was asked, and the request was not at fault.
- A daemon that stops answering while `start --wait` waits is `daemon-connection-failed`.

The daemon method is `start` (no parameter), which answers the result of `status`; the wait is the cli asking `status`
again, every quarter of a second, on a connection of its own, so the daemon has no timer to keep.

## Refusals

A refusal is the problem object on the standard error: exit code 2 when the request was refused, 1 when gabier failed
(`jdtls-start-failed`, `jdtls-start-timeout`, `daemon-start-failed`, `daemon-connection-failed`). The help of the command
(`gabier help start`) lists the keys:

- `jdtls-start-failed`: with `--wait`, jdtls failed to start (the reason follows when the daemon knows it).
- `jdtls-start-timeout`: with `--wait`, jdtls was not ready after the time given; it keeps starting, and the problem says where it was.
- `usage-error`: the arguments are not those of the command (gabier help <command> gives its usage).
- `daemon-start-failed`: no daemon answered and `gabier-daemon` could not be started.
- `daemon-connection-failed`: the connection to the daemon was lost, or it hung up without answering.
