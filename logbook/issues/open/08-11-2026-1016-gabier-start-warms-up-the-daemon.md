# gabier start warms up the daemon and returns when jdtls is ready

Opened: 08-11-2026 10:16

For 0.4.0. An agent starts its session with `gabier start` (in the background, or with `--wait`), works on something
else while jdtls indexes the project, and its first real command is then quick. Today the daemon starts only on the
first command, which pays the whole wait.

Done when:
- [ ] `gabier start` starts the daemon of the project when none runs and answers at once (`starting`, with the socket and
      the log); `gabier start --wait [--timeout=<seconds>]` returns once jdtls is ready, and refuses with a key and the
      last state of jdtls when the timeout passes; a daemon already ready answers `ready` and starts nothing;
- [ ] `--wait` writes the state to the standard error while it waits (never on the standard output) and none when the
      answer is quick; the exit code and the keys follow the other refusals;
- [ ] tests on the fake daemon of the cli for each case (not running, starting, ready, timeout, start failed);
      `gabier help start`, `gabier help`, `docs/cli.md` and `docs/daemon.md` say it, and the end message of
      `scripts/release-install.sh.template` cites it; `bin/ci` passes.

Depends on: status-shows-the-indexing-of-jdtls (the state that `--wait` waits on and prints).
