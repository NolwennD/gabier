# gabier start warms up the daemon and returns when jdtls is ready

Opened: 08-11-2026 10:16

For 0.4.0. An agent starts its session with `gabier start` (in the background, or with `--wait`), works on something
else while jdtls indexes the project, and its first real command is then quick. Today the daemon starts only on the
first command, which pays the whole wait.

Done when:
- [x] `gabier start` starts the daemon of the project when none runs and answers at once (`starting`, with the socket and
      the log); `gabier start --wait [--timeout=<seconds>]` returns once jdtls is ready, and refuses with a key and the
      last state of jdtls when the timeout passes; a daemon already ready answers `ready` and starts nothing;
- [x] `--wait` writes the state to the standard error while it waits (never on the standard output) and none when the
      answer is quick; the exit code and the keys follow the other refusals;
- [x] tests on the fake daemon of the cli for each case (not running, starting, ready, timeout, start failed);
      `gabier help start`, `gabier help`, `docs/cli.md` and `docs/daemon.md` say it, and the end message of
      `scripts/release-install.sh.template` cites it; `bin/ci` passes.

Depends on: status-shows-the-indexing-of-jdtls (the state that `--wait` waits on and prints).

## Resolution

Done in ef2c5a3 (pull request 26, merged fast-forward). `gabier start` starts the daemon of the project when none runs and
answers at once (`gabier-daemon started|running, pid N, project P, jdtls <state>; socket S[, log L]`, the log named only when
this call started the daemon); `--wait [--timeout=<seconds>]` (whole seconds from 1, 300 by default, refused without `--wait`)
returns once jdtls is ready and writes the state on the standard error with the existing `WaitNotice`, none when the answer is
quick. A daemon whose jdtls starts or is ready is started again by nothing. Daemon side: the request `start` (`StartMethod`,
`ServedProject.warmUp()`) answers the same JSON as `status`; a failed jdtls is started again as a request would, and a closed
server refuses with `language-server-not-ready`. Refusals: `jdtls-start-failed` and the new `jdtls-start-timeout` (the last
state and progress in its parameters), both exit 1; `daemon-start-failed`, `daemon-connection-failed` and `usage-error` as for
the other commands. `docs/commands/start.md`, `gabier help start`, `docs/cli.md`, `docs/daemon.md`, `docs/protocol.md`, the
README, the end message of the install and `.claude/rules/dogfooding.md` (an agent begins a session with `gabier start`) say it.

- Checked: the worker's `bin/ci` (format, build, test, release-install, native passed; the jdtls stage was cut by the worker's
  own timeout and rerun alone, passed in 644 s, including the new `InstalledDaemonTest` that proves the real jdtls: `start`
  answers `starting` at once, jdtls becomes ready with no other request, a second `start` starts nothing), and `bin-ci` green
  on the pull request (run 42483562241, 12 min 11 s, every stage, `--all`). The full `bin/ci` was not rerun as one run after
  the integration test was added; the check of the pull request is the full run.
- Proofs: `StartingJdtlsTest` (not running, running, ready, wait returns, timeout, stderr only, `--json`, refusals),
  `ProjectServerTest.WarmingUp`, `DaemonServerTest`, `DaemonAnswersTest`, `CommandRefusalsTest`, and the check `startWithTheFakeServer`
  of the native stage. Not covered by a unit test: `--wait` on a daemon this very call started (the native check covers it).
- Review (neutral, once): nothing blocking. Six suggestions in `08-11-2026-1409-gabier-start-review-suggestions.md` (on hold):
  two `start` at once both say "started", a silent daemon makes `--timeout` hang, an interruption reported as
  `daemon-connection-failed`, two `status` calls per round, a stacked Javadoc in `bin/ci.d/40-native` (fixed by the
  supervisor in a small pull request), no cli test of start after a failed jdtls.

Tokens:
- worker: 273912 tokens (several notifications while it waited on its own `bin/ci`; the last one is the figure).
- reviewer: 75469 tokens (1 notification).
- supervisor: 18 calls, 13631 output tokens, 37699 input + cache write (4421464 cache reads, apart), from 13:34 to 14:24 on
  08-11-2026 (12:34 to 13:24 UTC); exchanges with the user: 1 message.
