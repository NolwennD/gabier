# The daemon

One daemon runs per user. `gabier diagnostics`, `gabier rename`, `gabier organize-imports`, `gabier extract-variable` and `gabier extract-method` **start it when nothing answers on the socket**, so an
agent runs them in a project folder and gets the answer; `gabier status` and `gabier stop` never start one (see [status and stop](commands/status-and-stop.md)).
It can also be started by hand, with `gabier-daemon` from the installed distribution (`./gradlew :daemon:installDist`,
then `modules/daemon/build/install/gabier-daemon/bin/gabier-daemon`), and runs until `gabier stop` or a SIGTERM; it
answers `status`, `stop`, `diagnostics`, `rename`, `rename-token`, `organize-imports`, `extract-variable` and `extract-method`, and nothing else yet.

**Installing both by name.** `mise run install` builds the native cli and the daemon of the checkout it is run in and
links `gabier` and `gabier-daemon` into `~/.local/bin` (`GABIER_BIN_DIR` or the argument of `scripts/install.sh` names
another folder, which must be in the `PATH`): then `gabier` is called by name in any checkout. The links point into the
checkout that was built, so the daemon of a worktree that is removed is gone with it: install from the checkout the
tools should come from, and again after a change. A file that is not a link is never replaced. The build needs the
GraalVM 25 of `./gradlew :cli:nativeCompile` and a JDK for the daemon's start script (`JAVA_HOME`, else `java` on the
`PATH`).

**Starting on demand.** A request command (`diagnostics`, `rename`, `organize-imports`, `extract-variable`, `extract-method`) first connects to the socket: a daemon that answers is
used, never started a second time. Otherwise the cli launches `gabier-daemon` as a process, found **next to the binary
of the cli** (a distribution that ships the two together), else on the **`PATH`**: a native image cannot call into the
daemon, and the two are installed by name. The daemon starts in the cli's working directory with the cli's environment,
so it serves the project the daemon rules below give (`GABIER_PROJECT`, else the current folder; `GABIER_SOCKET`,
`GABIER_JDTLS` reach it too). It runs on after the command (no input; it stays in the process group of the cli, so a
Ctrl-C of the terminal during a later command also stops it), and its standard output and error go to
a log next to the socket, `<socket>.log` (`gabier.sock.log`; private to the user, replaced at each start, in the
socket's folder, which the cli creates private if it is missing). The cli waits until the socket accepts a connection,
**20 seconds** at most (the daemon binds first; jdtls starts later, at the first request, and that wait is the one
described below), then sends the request. A daemon that is not found, cannot be launched, exits before it listens (its
exit code is reported) or is silent for 20 seconds is the problem object `daemon-start-failed` (exit code 1; `socket`,
`log` and, for an exit, `exitCode` in its parameters, the output of the daemon being in the log): the daemon that did
not come up is killed, nothing is left running. A usage error starts nothing. A daemon that already runs keeps the
project it was started for, whatever folder the command is run in (`gabier status` says which): `gabier stop` first to
serve another.

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
standard error of the daemon, prefixed with `jdtls:`, with control characters written as escapes like in the text of
`gabier diagnostics`.

jdtls is ready when it says so itself: its `ServiceReady` status comes once its import is over, and the daemon then asks
the list of projects it imported, once. A folder with nothing jdtls can import (no Gradle, Maven or Eclipse build) is
refused at once, not after five minutes, with the problem object `no-build-to-import` (exit code 2). A jdtls that dies
while it is ready (killed, crashed) is seen as dead: `status` says `failed`, its exit code is on the standard error of
the daemon, and the next request ends what is left of it and starts another.

Two things to know before pointing the daemon at a folder. The project is **the directory the daemon is started in**
when no folder and no `GABIER_PROJECT` is given, so a daemon started in the wrong place serves the wrong project. And
jdtls importing a checkout **runs its build** (the Gradle wrapper or script, Maven, their plugins) at the first request:
do not serve a checkout you do not trust. Also, `gabier stop` usually returns at once, but it can last **about a
minute** when jdtls ignores the shutdown: the daemon gives it 10 s to answer the shutdown and 10 s to exit, then 10 s
for each process of its tree that it has to kill.

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
