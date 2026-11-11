# The daemon

One daemon runs per project. `gabier diagnostics`, `gabier rename`, `gabier organize-imports`, `gabier cleanup`, `gabier extract-variable`, `gabier extract-method`, `gabier extract-constant`, `gabier extract-field`, `gabier move-type-to-file`, `gabier move`, `gabier inline`, `gabier create`, `gabier references`, `gabier actions`, `gabier apply-action` and `gabier start` **start it when nothing answers on the socket**, so an
agent runs them in a project folder and gets the answer; `gabier status` and `gabier stop` never start one (see [status and stop](commands/status-and-stop.md)).
It can also be started by hand, with `gabier-daemon` from the installed distribution (`./gradlew :daemon:installDist`,
then `modules/daemon/build/install/gabier-daemon/bin/gabier-daemon`), and runs until `gabier stop`, a SIGTERM, or the delay with no client below; it
answers `status`, `start`, `stop`, `diagnostics`, `diagnostics-all`, `rename`, `rename-token`, `organize-imports`, `cleanup`, `extract-variable`, `extract-method`, `extract-constant`, `extract-field`, `move-type-to-file`, `move`, `inline`, `create`, `references`, `actions` and `apply-action`, and nothing else yet.

**Installing both by name.** `mise run install` builds the native cli and the daemon of the checkout it is run in,
**copies** them into `~/.local/share/gabier/<version>` (`<version>` is the one `gabier --version` prints; the folder is
`GABIER_INSTALL_DIR`, or the second argument of `scripts/install.sh`) and links `gabier` and `gabier-daemon` into
`~/.local/bin` to that copy (`GABIER_BIN_DIR`, or the first argument, names another folder, which must be in the
`PATH`): then `gabier` is called by name in any checkout. The copy holds `gabier`, the distribution of the daemon
(`daemon/bin`, `daemon/lib`) and `gabier-daemon`, a link to its start script, so the cli, which looks for the daemon next
to its own binary (the real file behind the link) before the `PATH`, starts the daemon of its own install, never another
`gabier-daemon` of the `PATH`. Nothing points into the checkout: `./gradlew clean`, a removed worktree or a checkout of
another version leaves the install working; install again to take a new build. A second install of a version replaces
its copy (a build with no `gabierVersion` is `0.0.0-dev`, always the same folder); the copies of other versions stay. An
existing link of the links folder is replaced; a file that is not a link is never touched, and the install stops before
building. The build needs the GraalVM 25 of `./gradlew :cli:nativeCompile`, and the daemon a JDK for its start script
(`JAVA_HOME`, else `java` on the `PATH`).

**Starting on demand.** A request command (`diagnostics`, `rename`, `organize-imports`, `cleanup`, `extract-variable`, `extract-method`, `extract-constant`, `extract-field`, `move-type-to-file`, `move`, `inline`, `create`, `references`, `actions`, `apply-action`) first connects to the socket: a daemon that answers is
used, never started a second time. Otherwise the cli launches `gabier-daemon` as a process, found **next to the binary
of the cli** (a distribution that ships the two together), else on the **`PATH`**: a native image cannot call into the
daemon, and the two are installed by name. The daemon starts in the cli's working directory with the cli's environment,
so it serves the project the daemon rules below give (`GABIER_PROJECT`, else the current folder; `GABIER_SOCKET`,
`GABIER_JDTLS` reach it too). It runs on after the command (no input; it stays in the process group of the cli, so a
Ctrl-C of the terminal during a later command also stops it), and its standard output and error go to
a log next to the socket, `<socket>.log` (`demo-6f1c0d4e.sock.log`; private to the user, replaced at each start, in the
socket's folder, which the cli creates private if it is missing). The cli waits until the socket accepts a connection,
**20 seconds** at most (the daemon binds first; jdtls starts later, at the first request, and that wait is the one
described below), then sends the request. A daemon that is not found, cannot be launched, exits before it listens (its
exit code is reported) or is silent for 20 seconds is the problem object `daemon-start-failed` (exit code 1; `socket`,
`log` and, for an exit, `exitCode` in its parameters, the output of the daemon being in the log): the daemon that did
not come up is killed, nothing is left running. A usage error starts nothing. A daemon that already runs keeps the
project it was started for (`gabier status` says which), and the command finds the one of its own project by its socket.

It serves **one project**: `gabier-daemon [<project folder>]`, else the folder named by `GABIER_PROJECT`, else the
root of the directory it is started in, the nearest folder from it up that holds a `.git` (a relative folder is relative
to that directory; one that is not a folder is a usage error).
Starting the daemon starts nothing else. jdtls is started by the
first request that needs it, or by `gabier start` (the daemon method `start`, which starts jdtls in the background and
answers at once, see [start](commands/start.md)), on that folder, and the request waits until it has imported the project: the first
`diagnostics` takes seconds for a small project and about a minute for this checkout (see "What the first call costs" below). Requests that arrive
meanwhile wait for the same start, never for another. jdtls then stays up for the next requests, and is ended, with every
process it started, by `gabier stop`, by SIGTERM, and when the daemon is stopped while jdtls is still starting. A jdtls
that does not start within **5 minutes** (the time of the whole start, then of each request to it), whose launcher is
missing or that exits is not a hang: the request gets the problem object `jdtls-start-failed` (exit code 1), whose
message says why, and the next request tries again. A launcher that exits at once is reported with its exit code. A
request that waits while the daemon stops is told that the daemon is stopping: its answer is written before its
connection ends (the daemon gives the clients 5 seconds to end, then cuts them). What jdtls says of itself goes to the
standard error of the daemon, prefixed with `jdtls:`, with control characters written as escapes like in the text of
`gabier diagnostics`.

**The cause of a failure is in the log.** A request that fails with an exception gabier does not know is answered with the
generic `internal-server-error`, with no description (the exception may hold a path or a secret). The daemon writes the
cause to its log, `<socket>.log`, with the method of the request: a line `request <method> failed: <type>: <message>`, then
a line `caused by <type>: <message>` for each cause, and, for an error that jdtls answered (`ResponseErrorException`), its
response error code and the data of its answer, one line of the log per line of it (jdtls puts the stack trace of its own
failure there). A refusal that carries a cause (a file that could not be written, an internal error that jdtls answered:
`language-server-request-failed`) is logged the same way; a refusal with no cause says everything in its answer and is not. The log holds the words of the
exceptions and nothing else: the parameters of the request (the file, the position, the name) and the text of the files
are not written on purpose, but those words may hold a path (a file that could not be read) or a type or symbol name (the
data of a jdtls error), and control characters are written as escapes like the lines of jdtls.

**Stopping when nobody uses it.** A daemon stops by itself, the way `gabier stop` stops it (jdtls and its workspace
folder are ended and removed, the socket is given back), once **no client has been connected for 60 minutes**: the delay
is `GABIER_IDLE_TIMEOUT`, in whole minutes, read when the daemon starts (`0` turns the rule off; anything else that is not a
whole number of minutes, 0 or more, is a `usage-error` that names the variable and starts nothing). The cli opens one
connection per call, so a call in progress holds the daemon up however long it lasts, and the delay is counted from
the end of the last connection (from the start of a daemon nobody has called yet): a `gabier status`, `gabier start` or
any other command, even one that is refused, restarts it. The daemon does not read the messages of the MCP server of
the cli (`ping` is answered by the cli itself), and the MCP server never stops the daemon when its host disconnects:
the daemon is shared with the other sessions and the command line. A connection that stays open with no call counts
as in use, so it keeps the daemon up. `gabier status` says the delay and the time left of it as it was when the call
arrived (the call itself restarts the delay when it ends), in the member `idle`, absent when the rule is off.

A request that reaches the daemon after it decided to stop, in the instant before it closes its listener, is answered
with the refusal `daemon-stopping` (exit code 1) and is **never run**: the daemon answers every line of such a
connection the same way and reads no parameter. A command that can start a daemon (every command but `status`, `stop`
and the polling of `start --wait`) takes that answer as the order to ask again: it waits until nothing answers on the
socket any more (it connects every 25 ms, for 5 seconds at most), starts a new daemon and sends the request to it, once.
So such a command is answered by a new daemon, and fails with `daemon-connection-failed` (its message says the daemon is
stopping) only when the old one is still there after the 5 seconds, or when the new daemon says it is stopping too. A
connection that ends with no answer at all, or a failure of any other kind, is **never asked again**, whatever the daemon was doing:
the request may have been run, and running it twice could change the files twice. It stays `daemon-connection-failed`.
`gabier status` and `gabier stop` do not ask again: they say `daemon-stopping`.

**What the first call costs.** The request that finds jdtls not started waits for all of this, in this order (measured
on this checkout, a Gradle build of five modules plus its `build-logic`, on 08-11-2026 on a machine shared with
another job; a first run under a heavier load took 105 s to be ready, and `04-11-2026-0122` measured 3 min 23 s on a repository of nested projects):

| Part | What happens | Time |
| --- | --- | --- |
| launch | the daemon opens the data folder and starts the jdtls process; jdtls starts its JVM and its OSGi runtime, up to its first `language/status` (`Init...`) | about 6 s |
| import | jdtls imports the project: for a Gradle project it starts a Gradle daemon and runs the build (compile of `build-logic`, evaluation of the settings, configuration of every module, the model of each); this is nearly all of the wait, and its `language/status` messages (`70% Starting Java Language Server - Configure project :core`) name the task | about 54 s |
| ready | `ServiceReady`, then the list of the projects jdtls imported | about 0.1 s |
| diagnostics | `diagnostics` and `diagnostics-all` only: the file (every file of the project, for the second) is opened, jdtls publishes, and the daemon waits for jdtls to be quiet for 5 seconds | 7 to 9 s for one file |

so the first `gabier diagnostics` of a session answers after about a minute (69 s in the measure, 112 s in the heavier run), the same call warm
after 6 to 9 s (the 5 seconds of quiet are in it), and a command that does not wait for the diagnostics (`rename`,
`organize-imports`, `references`, ...) after the import alone. Only the import depends on the project; a project with no
build file jdtls can read is refused at once, and a small Eclipse or Maven project imports in seconds. A Gradle daemon
that is still running from an earlier import shortens the import a great deal: a cold start of the daemon and of jdtls with
one warm answered the first `diagnostics` in 32 s.

**Ahead of time.** `gabier start` pays this wait before the first command: it starts the daemon if none runs, asks it to
start jdtls (nothing happens when jdtls is already starting or ready; a failed jdtls is started again), and answers at
once. `gabier start --wait [--timeout=<seconds>]` returns once jdtls is ready by asking `status` every quarter of a second
on connections of its own, and writes on the standard error the same line as the notice below while it waits; it refuses
with `jdtls-start-failed` (jdtls failed) or `jdtls-start-timeout` (300 seconds by default, the last state of jdtls in the
problem), both with exit code 1, and jdtls goes on starting in the daemon after a timeout.

**The notice.** A request command that has waited **3 seconds** while jdtls is starting writes one line on the
**standard error** (`gabier: waiting for jdtls (starting, 41s so far; importing the project for 38s: Configure project :core); the first
command of a session waits for it to start and import the project, the next ones are quick`; what jdtls is doing is the
one `gabier status` gives, see [status and stop](commands/status-and-stop.md)), and again every 15 seconds for as long as jdtls is starting, so an
agent can tell a wait from a hang. It asks the daemon for its `status` on a connection of its own while the request
waits, and writes nothing when the answer comes within 3 seconds, when jdtls is already ready (a slow request to a ready
jdtls, such as a `references` that waits for the indexes, gets no line), when the daemon does not answer the question,
and after the answer is written. The standard output is the answer alone, with or without `--json`; the line is on the
standard error in both, ahead of the problem object if the command fails (a program that parses the standard error of a
failed `--json` command reads its last line).

**Where the daemon finds jdtls.** At each start of jdtls, in this order: the launcher that `GABIER_JDTLS` names (used as it
is, even when nothing is there, so that the failure names it), else the jdtls shipped with the daemon, `jdtls/bin/jdtls`
in the folder above the `lib` folder of the daemon's own jar (the archive of a release holds `bin/`, `lib/` and `jdtls/`
side by side, so a gabier installed by mise brings its jdtls and switches with it), else the pinned jdtls that
`mise run jdtls:install` installs, `~/.local/share/jdtls/<version>/bin/jdtls` (the home folder is the one of the user; the version is the
`jdtls_version` of `mise.toml`, written by the build in the resource `jdtls.properties` of the daemon, so the number
exists once), else a `jdtls` on the `PATH`. So the daemon serves any Java project once `mise run jdtls:install` has run,
with no `mise.toml` and no variable in that project. With none of them, the request gets `jdtls-start-failed` whose
message names the places and `mise run jdtls:install`; the next request looks again, so a jdtls installed meanwhile
is found without restarting the daemon.

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

Two things to know before pointing the daemon at a folder. The project is **the root of the directory the daemon is started in**
(the nearest folder above it that holds a `.git`, else that directory) when no folder and no `GABIER_PROJECT` is given, so
a daemon started in the wrong place serves the wrong project. And
jdtls importing a checkout **runs its build** (the Gradle wrapper or script, Maven, their plugins) at the first request:
do not serve a checkout you do not trust. Also, `gabier stop` usually returns at once, but it can last **about a
minute** when jdtls ignores the shutdown: the daemon gives it 10 s to answer the shutdown and 10 s to exit, then 10 s
for each process of its tree that it has to kill.

**The socket** is per project, in a short place, because a Unix socket path holds at most 107 bytes (`sun_path` is 108,
one of them for the terminating zero) and a path under a checkout or a worktree easily exceeds it:
`$XDG_RUNTIME_DIR/gabier/<name>-<hash>.sock` (`/run/user/<uid>`), else `/tmp/gabier-<uid>/<name>-<hash>.sock`. The
project is the folder `GABIER_PROJECT` names (relative to the working directory), else the nearest folder from the
working directory up that holds a `.git` (a folder in a clone, a file in a worktree: the root of the project, whatever
folder of it the command is run in), else the working directory; it is taken as its real path. `<name>` is the name of
that folder, in plain characters (40 at most), so that the sockets can be told apart, and `<hash>` the first 8
hexadecimal digits of a hash of its path, so that two worktrees of one name do not share one. The cli and the daemon
compute it the same way (`SocketLocation`), so `gabier status` and `gabier stop` with no variable reach the daemon of
the project they are run in, and two worktrees have two daemons: a command run in one never reaches the other, nor
starts a second daemon over the first. The daemon serves the same folder (the one above, unless it is given another as
its argument, which is then the project of its socket too). `GABIER_SOCKET` replaces the whole path, for the daemon and
for the cli alike, and always wins. A path over 107 bytes (counted in UTF-8: a multi-byte name counts for
its bytes) is refused before anything is bound, with the `socket-path-too-long` problem, which gives the path, its
length, the limit and the variable to set. The folder of the socket must be the user's own and private to them
(mode 700; it is created so, and an existing one that is not is refused, never changed), and the socket file is made
`rw-------`. A daemon refuses to start while another one answers on the socket (`daemon-already-running`), removes a
socket file that nobody answers on (what a killed daemon leaves) but nothing else: a file that is not a socket of the
user's own is never removed.
