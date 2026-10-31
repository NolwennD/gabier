# `gabier` starts the daemon when it is needed

Opened: 31-10-2026 16:05

An agent runs `gabier rename` or `gabier diagnostics` in a project folder and gets the answer, without starting
`gabier-daemon` by hand first (today: `daemon-not-running` until someone does, README "Command line"). The cli starts the
daemon for the current project when none answers on the socket, waits until it accepts requests, then sends the request.
`status` and `stop` still never start one. A mise task installs `gabier` and `gabier-daemon` on the PATH, so that an
agent can call them by name in any checkout.

Done when:
- [x] A request command (`diagnostics`, `rename`) with no daemon running starts `gabier-daemon` for the project it is run
      in (the folder rules of the README: argument, `GABIER_PROJECT`, else the current folder), waits for the socket, and
      answers; shown against the fake daemon in `GabierTest` and in the native stage of `bin/ci.d/40-native`.
- [x] A daemon that does not come up (not found, exits at once, no socket before a stated delay) is a problem object with
      the reason and where its output went; nothing is left running; a running daemon is reused, never started twice.
- [x] `status` and `stop` with no daemon still answer `daemon-not-running` at once.
- [x] `mise run install` (name free) puts `gabier` and `gabier-daemon` on the PATH, and the README says how.
- [x] README ("Command line", daemon section) is updated; `bin/ci` green, coverage 100%.

## Resolution

Merged as `8bce856` (small fixes of the review in `15dd92c`). An agent runs `gabier diagnostics` or `gabier rename` in a
project folder with no daemon started by hand: the cli starts one for the current project, waits for its socket (20 s) and
answers. `gabier` and `gabier-daemon` go on the PATH with `mise run install` (`scripts/install.sh`, links into
`~/.local/bin`, or `GABIER_BIN_DIR`).

- How the cli finds the daemon: `DaemonLauncher` looks next to its own binary first, then on the PATH. A native image
  cannot call into the daemon, so it launches a process; the symlinks of `mise run install` resolve to build outputs, so
  there the PATH is what finds it. The daemon starts in the cli's working directory with the cli's environment, so its own
  rules pick the project (`GABIER_PROJECT`, else the folder); its output goes to `<socket>.log` (private, replaced at each
  start). Failures are problem objects (`daemon-start-failed`, exit 1: not found, exits at once with its exit code and log,
  no socket before the delay, log not writable), and what the cli started is killed. `status` and `stop` never start one.
- Replaced on purpose: the two `GabierTest` tests `saysNoDaemonIsRunningWithoutHanging` (diagnostics, rename), which
  asserted `daemon-not-running` for a request command; `GabierTest.StartingTheDaemon` and
  `statusAndStopStartNoDaemonEvenWhenOneIsOnThePath` cover them. README, skills `add-daemon-method` and `verify` updated.
- Proofs: `DaemonLauncherTest` and `GabierTest` against a real process (a shell script `gabier-daemon` running
  `FakeDaemonMain` on a real socket), the native stage `startsTheDaemonOnDemand` of `bin/ci.d/40-native` (starts from the
  PATH, next to the cli, not found, exits at once, reuse of the same pid, `stop`, `status`/`stop` start nothing) and a manual
  run of the native binary through the links of `mise run install`.
- Checked by the worker's full `bin/ci`: format 9s, build 4s, test 121s, native 17s, jdtls 298s; no stage skipped.
- Review: one pass, no blocking finding. Fixed by the supervisor after the merge (`15dd92c`): `install.sh` resolved a
  relative folder against the checkout and could half-install when the second name was a regular file; the README said
  "detached" while the daemon stays in the process group of the cli (a Ctrl-C of the terminal stops it).
  Opened on hold, `concurrent-first-requests-start-one-daemon`: two clients that find no daemon at once both replace the log
  (`DaemonLauncher.java:140-141`), and three tests that cannot fail for their stated reason (no test for the symlinked log,
  a reuse test that asserts the number of log lines instead of the pid, a native check that is vacuous when `status` fails).
- Known: a daemon already running keeps its own project, whatever folder the next command runs in (in the README).

Tokens:
- worker: 190145 (one notification)
- reviewer: 74474
- supervisor: 19 calls, output 11986, input + cache_write 32394 (cache read 1945608), over the period of the ticket, the
  `ship` step and the rename ticket's closing included; exchanges with the user: 0 messages
