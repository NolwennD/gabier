# gabier status and gabier stop

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

`gabier stop` returns once the daemon has removed its socket and hung up. With no daemon, or with a socket file that
nobody answers on, `status` and `stop` do not wait and do not start one: they report `daemon-not-running` at once, with
exit code 2.
