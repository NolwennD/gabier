# The daemon

One daemon runs per project. `gabier diagnostics`, `gabier rename`, `gabier organize-imports`, `gabier cleanup`, `gabier extract-variable`, `gabier extract-method`, `gabier extract-constant`, `gabier extract-field`, `gabier move-type-to-file`, `gabier move`, `gabier inline`, `gabier create`, `gabier references`, `gabier actions`, `gabier apply-action`, `gabier affected-tests`, `gabier test` and `gabier start` **start it when nothing answers on the socket**, so an
agent runs them in a project folder and gets the answer; `gabier status` and `gabier stop` never start one (see status and stop).
It can also be started by hand, with `gabier-daemon` from the installed distribution (`./gradlew :daemon:installDist`,
then `modules/daemon/build/install/gabier-daemon/bin/gabier-daemon`), and runs until `gabier stop`, a SIGTERM, or the delay with no client below; it
answers `status`, `start`, `stop`, `diagnostics`, `diagnostics-all`, `rename`, `rename-token`, `organize-imports`, `cleanup`, `extract-variable`, `extract-method`, `extract-constant`, `extract-field`, `move-type-to-file`, `move`, `inline`, `create`, `references`, `actions`, `apply-action`, `affected-tests`, `affected-tests-changed`, `test` and `test-changed`, and nothing else yet.

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
answers at once, see start), on that folder, and the request waits until it has imported the project: the first
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

**Stopping when nobody uses it.** A daemon stops by itself, the way `gabier stop` stops it (jdtls is ended and its workspace
is kept or removed as at `gabier stop`, the socket is given back), once **no client has been connected for 60 minutes**: the delay
is `GABIER_IDLE_TIMEOUT`, in whole minutes, read when the daemon starts (`0` turns the rule off; anything else that is not a
whole number of minutes, 0 or more, is a `usage-error` that names the variable and starts nothing). The cli opens one
connection per call, so a call in progress holds the daemon up however long it lasts, and the delay is counted from
the end of the last connection (from the start of a daemon nobody has called yet): a `gabier status`, `gabier start` or
any other command, even one that is refused, restarts it. The daemon does not read the messages of the MCP server of
the cli (`ping` is answered by the cli itself), and the MCP server never stops the daemon when its host disconnects:
the daemon is shared with the other sessions and the command line. A connection that stays open with no call counts
as in use, so it keeps the daemon up, and so does `gabier diagnostics --watch`, which stays connected for as long as it runs.
`gabier status` says the delay and the time left of it as it was when the call
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
| launch | the daemon opens the workspace of jdtls and starts the jdtls process; jdtls starts its JVM and its OSGi runtime, up to its first `language/status` (`Init...`) | about 6 s |
| import | jdtls imports the project: for a Gradle project it starts a Gradle daemon and runs the build (compile of `build-logic`, evaluation of the settings, configuration of every module, the model of each); this is nearly all of the wait, and its `language/status` messages (`70% Starting Java Language Server - Configure project :core`) name the task | about 54 s |
| ready | `ServiceReady`, then the list of the projects jdtls imported | about 0.1 s |
| diagnostics | `diagnostics`: the file is opened, jdtls publishes, and the daemon waits for jdtls to be quiet for 5 seconds. `diagnostics-all`: no file is opened; the daemon waits for jdtls to register the files it watches (the end of its initialization, which publishes the problems of the whole workspace), tells it of the changes on disk, asks for an incremental build, and waits 1 second in which nothing is published | 7 to 9 s for one file; 1.2 s for the whole project (532 files) once jdtls is started, 50 s for the first call right after the start of the daemon |
| affected-tests | `affected-tests` and `affected-tests-changed`: jdtls is told of the files on disk, asked for an incremental build, and awaited; then the daemon reads the class folders and the jars of the test classpath | 1 to 3 s on a small project once jdtls is started (about 1.1 s after the first call), plus the reading of the class files |
| test | `test` and `test-changed`: the build as for `affected-tests`, then a JVM of its own runs the tests (below) | on this checkout, one small class of 6 tests: 35 s for the first call after the daemon was ready (jdtls builds what it has not built yet), then 3.1 s for each next call, of which about 1.3 s is the build and the rest the JVM; 25 % less on the next calls with the AOT cache (below) |

so the first `gabier diagnostics` of a session answers after about a minute (69 s in the measure, 112 s in the heavier run), the same call warm
after 6 to 9 s (the 5 seconds of quiet are in it), and a command that does not wait for the diagnostics (`rename`,
`organize-imports`, `references`, ...) after the import alone. Only the import depends on the project; a project with no
build file jdtls can read is refused at once, and a small Eclipse or Maven project imports in seconds. A Gradle daemon
that is still running from an earlier import shortens the import a great deal: a cold start of the daemon and of jdtls with
one warm answered the first `diagnostics` in 32 s.

**What a restart costs.** This is for the first call of a daemon that starts on a project the previous daemon opened: it
is a first start only once, because jdtls keeps its workspace between two daemons (see "The workspace of jdtls" below).
Measured on 15-11-2026 with the pinned jdtls (1.63.0) on this checkout, on a machine of 16 cores with other jobs running (load
average 7 to 12), a warm Gradle daemon and warm shared indexes, with `JAVA_HOME` on a JDK 27 and several JDK 25 installed
(SDKMAN! and mise: the case that made a kept workspace slow):

| Start | `gabier start --wait` | Full builds of the project (trace of the Java builder of jdtls) |
| --- | --- | --- |
| first start, empty workspace | 68 and 69 s | 14: 7 for the import, 7 after it, once the daemon pinned the JDKs (12 s more, in the background, after the start is ready) |
| restart on the kept workspace, three in a row | 14, 12 and 12 s | 0 |
| restart after Java files were created, changed and deleted while the daemon was stopped | 8 s | not measured; `gabier diagnostics --all` then answers the same problems as after a first start (1409 lines, only their order differs), the new and the changed files with their errors and the deleted one with none |
| a start after the daemon was killed, on a jdtls that still runs on the workspace | 84 s | the start is a first start, on a temporary data folder |

What the figures are made of, from jdtls alone (a probe that starts the pinned jdtls on a data folder: its `initialize`, its
`ServiceReady` and the end of its build jobs, the file watchers registered): a new data folder is ready at the `ServiceReady`
after 14 to 37 s and its build jobs end after 67 to 83 s; a kept data folder that jdtls rebuilds as a whole (the JDK of the
projects changed from one start to the next, with no JDK declared) is ready after 36 to 45 s, which is the 30 s of the build
plus the start; a kept data folder it does not rebuild is ready after 9 to 12 s. That is the difference between the two
measures of `02-11-2026-1223` (a restart slower than a first start) and the ones above: jdtls rebuilt everything at
each restart because it bound the projects to another JDK of the same version, and the workspace now keeps the choice.

**Following the files while a client watches.** `gabier diagnostics --watch` (diagnostics) and
`gabier affected-tests --watch` (affected-tests) make the daemon look at the disk, and only
while a client watches (one thread and one watcher for the clients of both, a thread `gabier-problems`): the first
watcher starts it, the last one leaving ends it (a thread `gabier-problems`, and the system's registrations, are given back; nothing runs when nobody
watches). It asks the system to tell it of every change in the folders of the project: one registration per folder
(inotify on Linux, through the `WatchService` of the JDK: a registration covers one folder and not what is below it, so
every folder is registered, build folders included, and a folder created later when its creation is told; links are not
followed, as the scan of the files of the project does not), and keeps the events that concern a file jdtls asked to have
watched (its globs, which jdtls registers late in its initialization: the daemon waits for them first). Events that come
close together, such as the files of a rename, are one change (**100 ms** without event), and a change counts only when
the watched files no longer look as they did (their modification time and size), so jdtls writing its class files, or a
file touched with no change, reads nothing again. A change is then told to jdtls, which builds, and the daemon reads the problems of
the project as `diagnostics --all` does, **once for every client that watches the problems**. For the clients that watch
the tests, the daemon reads the tests of the changes on disk instead (what `affected-tests` with no file answers, which
uses those changes up), **once for every client that watches the tests**, and only while one does: a watcher of the
problems alone reads no test, so it never uses the changes up. The first read of the thread that starts reads no test, as
the request of the watcher answered with the changes so far.

Why the daemon follows the disk itself: jdtls refreshes the files from the `workspace/didChangeWatchedFiles` events its
client sends, as the protocol expects of a client, and not from the disk. Its own refresh is a preference of the Eclipse
workspace (`refresh.enabled`), which can only be set in a file of its data folder, whose monitor on Linux is a poll of 4
seconds at the least, and which was slow when it was tried: 2 to 8 seconds on a small project and 7 to 23 seconds on this
checkout, against 1.1 to 1.3 seconds and 12 to 16 seconds with the events the daemon sends.

**How `test` runs the tests.** The daemon never loads a test. For `test` and `test-changed` it asks the build as `affected-tests`
does, refuses when the file of a test class to run does not compile, then starts a JVM of its own: the `java` of the daemon, in
the root of the project, on the class path jdtls gives for the tests (the class folders and the libraries of every project of
the build, together) followed by the daemon's own jar. A small class of the daemon, `TestRunnerMain`, runs in that JVM and calls
the JUnit Platform **of the project** by reflection (`LauncherFactory`, the engines it holds): gabier adds no JUnit to the class
path and no jar to itself, so the versions are the project's. It writes the report (counts, and for each failure the class,
method, exception, message and the first stack frame whose class file is in a class folder of the project) to a file, which the
daemon reads; what the tests print goes to a log kept only to say why a JVM ended with no report. The JVM is killed, with its
descendants, when the time limit passes (`testing-test-timeout`) or when it ends with no report (`testing-run-failed`).
Limits: the class path is the union of the projects of the build, so a build whose projects need different versions of a
library runs a test with one of them (observed on this checkout: the Gradle API jars of `build-logic` are on it); the JVM is
the daemon's, not the one the project is built for; the class path needs the launcher (`org.junit.platform:junit-platform-launcher`),
which Maven and Gradle projects often do not declare, since Surefire and the Gradle test task bring their own (the
engine and the platform engine jars do not depend on it): without it the call is refused (`testing-no-test-launcher`); this
checkout declares it, and a project with only JUnit 4 has none. A Java file with an error other than a test class does not
refuse the call (jdtls flags dozens of files of this checkout, such as test fixtures it cannot see across modules): a test that
reaches the class it wrote for such a file fails with "Unresolved compilation problem".

**The AOT cache of the test JVM.** Started with `GABIER_TEST_AOT_CACHE=on` in its environment (read when the daemon starts; the
cli passes its own to the daemon it starts), the daemon starts the JVM of the tests with `-XX:AOTCache=<file>`, a cache (JEP 483) of
the classes of the dependency jars: the launcher, the engines, the libraries of the tests. `gabier help test` says what the agent
sees. It is a setting of the daemon and not an option of `test` because a request has no optional parameter (an option would be
required of every client, the MCP tools included) and the cache is a choice for a machine, not for a call; its cost is that a
daemon that runs does not see a change of the variable.

- **What is cached.** The dependency jars of the class path of the tests (the files that are not under the root of the project: a
  jar that a build writes in the project is rewritten at each build, so it stays out) and the jar of the daemon, which holds
  `TestRunnerMain`. A class folder cannot be on the class path of a cache's creation (`Cannot have non-empty directory in
  paths`, observed), so the class path of the tests is, with the setting on, the dependency jars, the jar of the daemon, then the
  rest of the class path of the project in its order (the class folders): the cache is a prefix of it, as the JVM asks. The
  order is the same whether the cache exists yet or not. Its cost is that a class of the project that has the name of a class
  of a library, or of the daemon's jar (the sources of gabier itself: its tests of `eu.gabier.daemon.TestRunnerMain` run the
  installed copy), is shadowed by the jar's.
- **How it is made.** At the first call (or the first after a change), before the tests and not counted in their time limit, a JVM
  of its own, `java -XX:AOTCacheOutput=<file> -cp <those jars> TestRunnerMain <report> ""`, runs the runner with no test class,
  which loads the classes of the launcher and the Jupiter engine, and the JDK assembles the cache at its end (about 6 to 8 s,
  2 minutes at the most, then it is killed). The classes of the tests themselves are not in the cache (a training run cannot
  have the class folders), so a library that only the tests load, AssertJ for instance, starts as it did: 2104 of 3236 classes
  of a run of `RenameTest` came from the cache in the measure below. The file is written under a temporary name and moved in
  place, so two daemons never see half of one.
- **When it is stale, it is not used.** JDK 25 and 26 do not check the jars a cache was made from (observed: a jar rebuilt at
  the same path runs from the old cache, with no warning), so the file is named by a key that holds everything it is valid
  for: the `release` file of the JDK (its build), the path, size and date of each cached jar in their order, and the same of
  the daemon's jar. A change to any of them is another key, so another cache; a JDK that does check (27) is never asked. The
  tests of `ProcessTestRunnerAotCacheTest` show each case on a real JDK 25 (`gabier.testJdk`, the Java 25 of the build; the same
  tests pass on JDK 27 with `-PgabierTestJdk=<home>`): a library, a runner and a jar of the daemon that change in size, or in
  date alone, are not run from the old cache, which holds the class that changed.
- **When it cannot be made or used, the run goes on with no cache.** A JDK before 25 (`release` says so: the one-step creation
  needs `-XX:AOTCacheOutput`), a daemon that does not run from a jar, a creation that fails (its last line of output is in the
  log of the daemon), a folder of the cache that cannot be written (a full disk, a file where the folder should be), a creation
  that takes too long: the log says `the tests start with no AOT cache: <why>`, and a failed key is written in `<key>.refused` so
  the creation is not tried at each call (a jar, the JDK or the daemon that changes is another key). A JVM that is given a cache
  and cannot use it (ZGC, observed; a very large heap or a `-javaagent` are named by the JEP) prints a warning and runs on without it
  (`-XX:AOTMode=auto`, the default).
- **Where, and how they are removed.** `<cache>/test-jvm/<project>/<key>.aot` (`<cache>` as for the shared indexes above, `<project>`
  as for the workspaces), about 20 MB, and the file `root` that names the project. A project keeps the cache of its current key:
  when a new one is made, the files of the other keys are removed, and so are the folders of the projects whose root no longer
  exists (a deleted worktree). That happens when a cache is made, so a machine that no longer asks for one keeps what it has; `rm -r
  <cache>/test-jvm` empties it.

Measured through the daemon (`gabier test eu.gabier.testing.domain.TestFiltersTest`, 12 tests, on this checkout; the daemon
started on JDK 25.0.2 and JDK 27, Temurin, neither GraalVM; 16 cores, load average 7 to 10 from other work; the first call of
each daemon not counted, then 10 calls, medians; `GABIER_TEST_AOT_CACHE` unset against `on`):

| JDK | no cache | cache | first call with the cache | cache size |
| --- | --- | --- | --- | --- |
| 25.0.2 | 4.16 s (3.96 to 4.53) | 3.12 s (2.89 to 3.24) | 28.7 s against 21.2 s (+7.5 s) | 20 MB |
| 27 | 4.31 s (3.84 to 4.44) | 3.04 s (2.84 to 3.06) | 25.0 s against 19.2 s (+5.8 s) | 20 MB |

A call is 1.0 to 1.3 s faster (25 to 30 %), about what the closed ticket measured on the JVM alone (0.65 s of 1.6 s), and the
build, the socket and the protocol (about 2 s) are what is left. To measure again: start the daemon twice, once with the
setting, with its own `GABIER_SOCKET` and `GABIER_CACHE`, and time `gabier test <class>` against each.

**What the daemon remembers of the disk, for `affected-tests`.** `gabier affected-tests` with no file
(affected-tests) is answered from a list the daemon keeps in memory: the Java files that were
created or edited on disk, as jdtls was told of them, since the last answer of that call. Every request takes in the
changes of the disk before it asks jdtls (a `diagnostics`, a `rename`, a `references`), and each one adds the Java files it
finds to the list; none removes one, only an answer of `affected-tests` does, and only for the files it answered for (a file
that changed again since stays). The list belongs to the daemon of the project: two clients that ask share it, and whichever
asks first is answered. It starts empty with jdtls, from the state of the disk read just before jdtls starts, so what
changes while jdtls imports the project is in it, and what changed before the daemon started is not. It is lost when the
daemon stops (`gabier stop`, or the hour with no request after which it stops itself) and when the daemon starts another
jdtls after a failure. The class files that jdtls writes are not used for it: a jdtls that starts rewrites all of them with no
edit (observed: after a stop and a start of the daemon on a project of four classes, every class file, those of untouched
classes included, was written again).

What it costs and how late it is, **measured on Linux** with jdtls 1.63.0, one machine shared with other jobs
(**measurements, not guarantees**; the JDK on other systems polls slowly, about every ten seconds on macOS, and these
delays are not those of such a system):

| | small project (14 files) | this checkout (2578 files with its build folders) |
| --- | --- | --- |
| registering the folders | 24 ms | 205 ms (about 1 s for 2146 folders when the disk is cold) |
| idle, nothing changed | no read, no CPU | no read, no CPU |
| the daemon sees a change after the write | about 100 ms (the debounce) | 0.4 to 0.5 s |
| the problem is shown after the write | 1.1 to 1.3 s | 12 to 16 s |

The delay of the whole is the read of jdtls (a build, about 1 s on a small project, 11 to 15 s on this checkout after an
edit): the daemon adds about half a second at most. A file that breaks and is fixed between two reads is not shown. The
cost is one watch per folder against the system limit of the user (`fs.inotify.max_user_watches`, 240210 on the machine
of the measure; a project with more folders than that cannot be followed this way). When the system refuses a
registration or says that it lost events, the daemon does not stay blind: it gives the registrations back, writes
**one** notice to every watcher (`gabier: notice: The system cannot follow the files of the project: they are looked at every
second from now on`, the text is fixed), reads the project once more, and from then on compares the files that jdtls
watches **every second**. A comparison is a walk of the project folders (**263 to 726 ms** on the 2578 files, 42 to 64 ms
on a worktree without its build folders, 0.7 to 1.3 s on 43,000 files, measured at other moments of the machine), so on a large tree this
fall-back uses a good part of a core for as long as a client watches, and does not read the project again unless a watched
file changed. With the walk at that pace, the change is seen after 0.4 to 1.4 s and the problem is shown as late as with
the events. A client that has a watch is a connected client: the daemon does not stop by itself for being idle while one
runs.

**Ahead of time.** `gabier start` pays this wait before the first command: it starts the daemon if none runs, asks it to
start jdtls (nothing happens when jdtls is already starting or ready; a failed jdtls is started again), and answers at
once. `gabier start --wait [--timeout=<seconds>]` returns once jdtls is ready by asking `status` every quarter of a second
on connections of its own, and writes on the standard error the same line as the notice below while it waits; it refuses
with `jdtls-start-failed` (jdtls failed) or `jdtls-start-timeout` (300 seconds by default, the last state of jdtls in the
problem), both with exit code 1, and jdtls goes on starting in the daemon after a timeout.

**The notice.** A request command that has waited **3 seconds** while jdtls is starting writes one line on the
**standard error** (`gabier: waiting for jdtls (starting, 41s so far; importing the project for 38s: Configure project :core); the first
command of a session waits for it to start and import the project, the next ones are quick`; what jdtls is doing is the
one `gabier status` gives, see status and stop), and again every 15 seconds for as long as jdtls is starting, so an
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

**The workspace of jdtls.** jdtls keeps its workspace (its `-data` folder, about 210 Mo for this checkout: the projects it
imported, its build of them, its indexes) in a folder of its own for each project,
`<cache>/jdtls-<version>/workspaces/<project>`, where `<cache>` is the folder of the shared indexes above (`GABIER_CACHE`, ...),
`<version>` the version of jdtls (a workspace of one version is not readable by another) and `<project>` the name of the
socket of the project without its `.sock` (`<name>-<hash>`, from the path of the project). **The daemon keeps it from one
daemon to the next**, so that a daemon that starts on a project the previous one opened has jdtls import and build nothing
again: the start measured below goes from about a minute to 12 to 14 s.

What makes a workspace reusable, and what makes the daemon start jdtls on an empty folder instead (a first start, which is
what a project with no workspace gets):

- **A clean end.** The daemon writes the file `.gabier-state` in the workspace only when jdtls was ready and left by itself
  after the shutdown (it exited by itself in time), and removes the file when it starts on the workspace. A jdtls that
  was killed, a daemon that was killed (SIGKILL, a crash, a power cut), a start that failed or that was stopped before
  jdtls was ready leave no state file, so the next start empties the folder.
- **The same jdtls and the same JDK.** The state holds a fingerprint: the version of jdtls and the JDK it runs under (the
  one of the daemon: its folder and its version). Another jdtls or another JDK, and a JDK that a project is bound to and
  that is not the one it was (see below: its folder holds another build, or none), empties the folder. A state that
  cannot be read does too.
- **A start that fails on a kept workspace is run again once on an empty one**, and says so in the log, so that no start
  fails or answers wrongly because of what a previous one left. The start that follows, on an empty folder, is a first
  start. A folder with nothing jdtls can import is refused at once, as before, without a second try.
- **One daemon at a time.** The daemon holds the lock `.gabier-lock` of the workspace (see below) as long as it runs
  jdtls on it. A workspace that another daemon holds (two daemons on one project: a socket given by `GABIER_SOCKET`),
  that cannot be made or emptied, or of a jdtls whose distribution tells no version, is not kept: the daemon says why in
  its log and uses a temporary data folder (`gabier-jdtls-data-<random>` in the temporary folder), which it removes when it
  ends jdtls (`stop`, SIGTERM, a failed start), as the daemon did before it kept workspaces.

*The files changed while the daemon was stopped.* jdtls does not look at the disk by itself (see "Following the files"
above), and the files an editor or a `git checkout` changed while it was stopped are not in its workspace. The daemon
writes with the state what jdtls was told of the files it watches (their modification time and size, as it keeps them
while it runs), and a daemon that starts on the workspace compares the disk with that, not with the disk of its start:
created, changed and deleted files are told to jdtls as soon as it registers its watchers, which is what the first request
does for a file that changes while jdtls imports. So `gabier diagnostics` answers as after a first start.

*The JDK of the projects.* A project asks for an execution environment (`JavaSE-25`), and jdtls binds it to one of the JDKs
it finds of that version; with several of them on the machine (SDKMAN!, mise, the system) the one it picks changes from one
start to the next, and a project bound to another JDK is built again as a whole. That is what made a kept workspace slower
than a new one when it was first tried. jdtls keeps in its workspace the JDK that a client declares for an environment
(`java.configuration.runtimes`) as its default, whatever order it finds the JDKs in. The daemon therefore asks jdtls, once
it is ready, which JDK each project is bound to (`java.project.getSettings`, `org.eclipse.jdt.ls.core.vm.location`), declares
them to it when the workspace is new, and writes them in the state, with the version of each JDK (its `release` file).
On a first start the declaration makes jdtls build again (the 7 builds after the import, in the background); on a restart it is not sent again, so nothing is built. What the client says in its initialization is not
enough: a jdtls that starts on a workspace builds before it reads it, with what the workspace kept.

*What is removed, and when.* A workspace is the user's cache, a folder of the cache of gabier. A daemon that opens a workspace
removes the ones of projects whose folder no longer exists (a deleted worktree) and that no daemon holds. `rm -r
<cache>/jdtls-*/workspaces` removes them all, which only makes the next starts first starts. The temporary data folders
of daemons that were killed are removed as before: when it starts jdtls the daemon removes the `gabier-jdtls-data-*`
folders that no live jdtls uses. The daemon holds a lock on the file `.gabier-lock` of the data folder as long as it runs
jdtls on it, and the system releases it when the daemon dies, killed or not (jdtls takes no lock of its own: the
`.metadata/.lock` of Eclipse is the one of its IDE, absent from the data folder of a running jdtls 1.63). It never touches the
folder of a running jdtls (the daemon of another project holds its lock), a folder that is not the user's, a link, nor a
temporary folder that has no lock file yet and is younger than 5 minutes (the time a jdtls may take to start: it is the
folder of a jdtls that is starting). What it cannot remove is told in its log, and does not stop jdtls from starting.

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
