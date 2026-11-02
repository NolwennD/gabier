# The daemon

One daemon runs per user. `gabier diagnostics`, `gabier rename`, `gabier organize-imports`, `gabier cleanup`, `gabier extract-variable`, `gabier extract-method`, `gabier extract-constant`, `gabier extract-field`, `gabier move-type-to-file`, `gabier move`, `gabier inline`, `gabier create` and `gabier references` **start it when nothing answers on the socket**, so an
agent runs them in a project folder and gets the answer; `gabier status` and `gabier stop` never start one (see [status and stop](commands/status-and-stop.md)).
It can also be started by hand, with `gabier-daemon` from the installed distribution (`./gradlew :daemon:installDist`,
then `modules/daemon/build/install/gabier-daemon/bin/gabier-daemon`), and runs until `gabier stop` or a SIGTERM; it
answers `status`, `stop`, `diagnostics`, `rename`, `rename-token`, `organize-imports`, `cleanup`, `extract-variable`, `extract-method`, `extract-constant`, `extract-field`, `move-type-to-file`, `move`, `inline`, `create` and `references`, and nothing else yet.

**Installing both by name.** `mise run install` builds the native cli and the daemon of the checkout it is run in and
links `gabier` and `gabier-daemon` into `~/.local/bin` (`GABIER_BIN_DIR` or the argument of `scripts/install.sh` names
another folder, which must be in the `PATH`): then `gabier` is called by name in any checkout. The links point into the
checkout that was built, so the daemon of a worktree that is removed is gone with it: install from the checkout the
tools should come from, and again after a change. A file that is not a link is never replaced. The build needs the
GraalVM 25 of `./gradlew :cli:nativeCompile` and a JDK for the daemon's start script (`JAVA_HOME`, else `java` on the
`PATH`).

**Starting on demand.** A request command (`diagnostics`, `rename`, `organize-imports`, `cleanup`, `extract-variable`, `extract-method`, `extract-constant`, `extract-field`, `move-type-to-file`, `move`, `inline`, `create`, `references`) first connects to the socket: a daemon that answers is
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
Starting the daemon starts nothing else. jdtls (the launcher `GABIER_JDTLS` names, as for the integration tests) is started by the
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

**The shared indexes.** jdtls indexes the JDK and every jar of the project (`~/.gradle`, `~/.m2`) so that a search or a
rename can read them; alone, each jdtls does it again (about 210 Mo in its data folder for this checkout). The daemon
starts jdtls with the system property `jdt.core.sharedIndexLocation` (through the launcher's `--jvm-arg=`), so that
jdtls copies the index of each external jar to a folder shared by all projects once its import is over, and reads it from
there in the next project instead of building it: `$GABIER_CACHE/jdtls-<version>/indexes`, else
`$XDG_CACHE_HOME/gabier/jdtls-<version>/indexes`, else `~/.cache/gabier/jdtls-<version>/indexes` (a relative path is
ignored). There is one folder per version of jdtls (the version of its `org.eclipse.jdt.ls.core` bundle, read in the
distribution of the launcher), because an index is not readable by another version; a launcher whose distribution tells
no version shares nothing, and the daemon says so in its log. The folder holds one file per jar (named by a hash of the
path of the jar, under a folder of the version of the index format) and is only a cache: to empty it, remove the folder
(`rm -r ~/.cache/gabier`), jdtls builds the indexes again and copies them back. What it saves is the indexing that
follows the import, not the import: the first `diagnostics` of a project is not faster (about 31 s for this checkout
with and without), the first `references` or `rename` that has to wait for the indexes is (about 29 s instead of 47 s).

**The data folder of jdtls.** jdtls keeps its workspace (its `-data` folder, about 210 Mo for this checkout) in a folder
of its own in the temporary folder, `gabier-jdtls-data-<random>`, which the daemon removes when it ends jdtls (`stop`,
SIGTERM, a failed start). A daemon that is killed (SIGKILL, a crash, a power cut) cannot, and `/tmp` is a memory file
system on many machines, so **when it starts jdtls the daemon removes the `gabier-jdtls-data-*` folders that no live jdtls
uses**: the daemon holds a lock on the file `.gabier-lock` of the data folder as long as it runs jdtls on it, and the
system releases it when the daemon dies, killed or not (jdtls takes no lock of its own: the `.metadata/.lock` of Eclipse
is the one of its IDE, absent from the data folder of a running jdtls 1.63). It never touches the folder of a running jdtls (the daemon of another project holds its lock), a folder
that is not the user's, a link, nor a folder that has no lock file yet and is younger than 5 minutes (the time a jdtls
may take to start: it is the folder of a jdtls that is starting). What it cannot remove is told in its log, and does not
stop jdtls from starting.

**Keeping the workspace between daemons.** By default nothing is left behind: the workspace is temporary, so a restarted
daemon imports the project again. `GABIER_JDTLS_WORKSPACES=<folder>` keeps it instead: the data folder is
`<folder>/<hash of the root of the project>` (the root is the real path of the project folder; a relative folder is
relative to the daemon's working directory), the same for every daemon of that project, and it is left as jdtls wrote it
when the daemon ends jdtls. The folder also holds `.gabier-root`, the root it is for. **The option is off**, for
this project too (decision of 02-11-2026): it was measured slower, not faster (45 to 51 s against about 30 s for the
first `diagnostics` after a restart, see "What it costs" below), so `mise.toml` does not set it. It stays delivered for
whoever wants its state kept (`GABIER_JDTLS_WORKSPACES=~/.cache/gabier/jdtls-workspaces`, for instance).

- **One daemon per workspace.** Two jdtls on one workspace corrupt it, and jdtls takes no lock of its own, so the daemon
  does: a second daemon on the same root (another socket, `GABIER_SOCKET`) while the first runs jdtls is refused at its
  first request with the problem object `workspace-in-use` (exit code 2) that says why and which variable to unset.
- **What it costs and what it saves** (this checkout, measured 02-11-2026 on a loaded machine, shared indexes warm,
  the first `diagnostics` after a restart): about 30 s with a temporary workspace (30.0, 29.8, 29.7 s) and **45 to 51 s
  with the kept one** (51.0, 44.8 s). jdtls reuses the projects of the kept workspace that are valid, but deletes and
  imports again the ones outside its root (`StandardProjectsManager.deleteInvalidProjects`), which is all of them here,
  so the restart is not faster, it is slower. What is kept is state, not time. A kept workspace weighs about 3 Mo once
  the daemon is stopped and 60 Mo while jdtls runs, with the indexes shared ("The shared indexes" above); without
  them (`GABIER_CACHE` empty) it is the 210 Mo of the data folder.
- **Cleaning.** `gabier remove-workspace [<project folder>]` removes the workspace of a project, with no daemon (see its
  [page](commands/remove-workspace.md)); the skill `ship` runs it before `git worktree remove`. When it starts jdtls the
  daemon also removes the kept workspaces whose project folder no longer exists (a worktree deleted without it), and
  never one that a daemon holds. To empty everything by hand, remove the folder: `rm -r ~/.cache/gabier/jdtls-workspaces`
  (with no daemon running).

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
