# gabier affected-tests

`gabier [--json] affected-tests <file>...` lists the test classes to run again after a change to Java files, with no
Maven or Gradle and whatever the build tool of the project (Infinitest does it for a developer in an IDE; gabier does it
for an agent). `gabier [--json] affected-tests`, with no file, does it for what changed on disk since its last answer
([see below](#with-no-file)), and `gabier [--json] affected-tests --watch` keeps running and prints the tests of each
change as it is made ([see below](#following-the-tests)). One line per test, the binary name of the class (`demo.LedgerTest`, `demo.Outer$InnerTest`), in the order
of the names. Nothing is written and nothing is run: the names are classes, which a test runner takes as they are. The
files are typed as paths, from the working directory, and the cli sends their absolute paths to the daemon, which
alone cleans them (`./src/...` and `src/../src/...` name the file they lead to). The exit code is 0 whenever the daemon
answered.

```text
$ gabier affected-tests src/main/java/demo/Ledger.java
demo.LedgerTest
demo.app.BankTest
```

A changed test class is affected itself. A changed class that the tests of the project depend on, directly or through
other classes, affects those tests. JUnit 3, JUnit 4, JUnit 5 and TestNG tests are recognised. A change that affects no
test prints `no affected tests` and exits with 0.

**`--json`** gives the result as the daemon gave it, an empty list when no test is affected:

```text
$ gabier --json affected-tests src/main/java/demo/Ledger.java
{"tests":["demo.LedgerTest","demo.app.BankTest"]}
```

In text, every line is neutralised for the terminal, as a class name comes from a class file, which is hostile text: see
[Text in a terminal](../cli.md#text-in-a-terminal). A file name that holds a line break is a usage error: the daemon
gets the files one to a line.

**A file that is not a Java source** (a `pom.xml`, a readme) is left out and does not refuse the call. A Java file that
does not exist is refused, a deleted file included: its tests cannot be told from a class that is gone.

## With no file

```text
$ gabier affected-tests
demo.LedgerTest
$ gabier affected-tests
no affected tests
```

The daemon keeps the Java files that were created or edited on disk since it was last answered for, as jdtls was told of
them ([the daemon](../daemon.md)): the agent names no file. The first call lists the tests of those files, as the call with
files does, and forgets them; a second call with no new edit prints `no affected tests`. The output, the order and
`--json` are the same as with files, and so is what is selected (the sections below hold for both).

- **Any edit counts**: by hand, by a text edit of an agent, or by a `gabier` command such as `rename` (the daemon tells jdtls
  of the files that changed on disk before every request, and each of them adds the Java files to the list, whatever the
  request is: a `gabier diagnostics` in between does not use the changes up). A file that is not Java is left out.
- **A refused call keeps the changes**: a file that does not compile is refused as `testing-compilation-failed` (`gabier
  diagnostics` says why), and so is a server that is not ready; the next call that is answered lists the tests of those
  changes too, with what changed since.
- **A deleted file, and a Java file under no source folder, are left out**, never refused (the call has no file of its own
  to be wrong about). A rename leaves the old file deleted, the new one created and the files that used the class
  edited: the last two are listed. The tests that depended on a class that was deleted are listed only when their own file
  changed; otherwise they do not compile, and `gabier diagnostics --all` says so.
- **The first call of a daemon answers for what changed since the daemon started**, not for the whole project: the state
  of the disk is read just before jdtls starts, and what changes while jdtls imports the project is counted. A daemon
  that is started by this very command (nothing was running) has seen nothing change, so it prints `no affected tests`:
  run `gabier start` before editing to make the first call useful. The choice is made so that no call answers every test
  of a large project by surprise; to run them all, give the files.
- **The state is the daemon's, not the client's**: two clients of the same daemon share it, and the one that asks first is
  answered (the second prints `no affected tests`); a watcher ([below](#following-the-tests)) is a client too. When the daemon stops (`gabier stop`, or after an hour with no request)
  or starts another jdtls after a failure, the changes made before are forgotten.
- **The class files are not the source of the changes**: observed on the real jdtls, a stop and a start of the daemon
  rewrote every class file of a project, those of classes nobody had touched included, so they would list every test after
  each start. For a body edit by hand and by a text edit, and for a rename, the files jdtls was told of and the class
  files it rewrote named the same classes; they differed where jdtls recompiles other classes than the ones edited (a
  changed signature, a deleted class): the class files show the dependents, the files told of do not, and the selection
  finds the dependents of a changed file by itself.

## Following the tests

```text
$ gabier affected-tests --watch
demo.LedgerTest
demo.app.BankTest
```

`gabier [--json] affected-tests --watch` does not end: it prints the tests of the changes so far (what a call with no file
answers, and it uses those changes up as that call does; nothing when no test is affected), then, after each series of
changes on disk, **the tests they affect**, one per line and each as soon as it is known, in the output of the call with
no file. It is what Infinitest does in an IDE, with no IDE and no build tool: start it once and read what comes. A change
that affects no test prints nothing (not `no affected tests`). Several saves that come close together (a rename, a
formatter) are one change and one answer: the daemon waits for 100 ms without an event before it looks, and a change
counts only when the files that jdtls watches no longer look as they did. It takes no file and no `--dry-run`: both are
usage errors, with exit code 2 and nothing asked of the daemon. The command lists the tests, it runs none.

- **`--json`** prints one `{"tests":[...]}` object per line, as the call does, and nothing for a change that affects no
  test; a notice is `{"notice":"..."}`.
- **The follow of `diagnostics --watch`, not another one**: the daemon follows the disk with one thread and one watcher
  for every client that follows something, the problems or the tests ([the daemon](../daemon.md)), and reads what a kind
  of client follows only while one watches it: a watcher of the problems alone reads no test, so it never uses the changes
  up. If the daemon cannot follow the disk any more it says so with a line `gabier: notice: ...` and goes on, as
  `diagnostics --watch` does.
- **A change that does not compile is not answered as "no test affected"**: the refusal (`testing-compilation-failed`, and
  the other refusals of the testing context: `testing-invalid-test-filter`, `testing-read-failed`) is printed on the
  standard error as the problem object, as the text or the JSON of any refusal, and the command **goes on**. The changes
  are kept: once the code compiles, the next change on disk lists their tests. A file that does not compile when the
  command starts is told the same way, after an empty start. That is also what the call does with no file (it keeps the
  changes), except that the call ends with the refusal.
- **Who uses up the changes**: whoever answers them first, the watcher or a call with no file, as the daemon keeps one
  list of changes for the project. The watcher reads the changes after each change on disk, so a call with no file made
  after the watcher printed its tests answers `no affected tests`, and the watcher prints nothing for the changes a call
  answered just before. **Two watchers** are told the same tests: the daemon reads the changes once for each change on
  disk and tells every watcher, so neither takes tests from the other. A watcher that reads slowly is told the tests of
  the changes it missed together, once each, in the order of the names: none is lost. (A call and a read of the watcher
  that overlap in time can both be answered the same changes: the tests are then printed twice, and none is lost.)
- **It ends** as `diagnostics --watch` ends: when it is killed (Ctrl-C or SIGTERM: the process ends with the status of
  the signal and prints nothing), with the problem of the daemon, exit code 2, when the daemon ends the subscription (the
  language server cannot be read any more), and with `daemon-connection-failed`, exit code 1, when the daemon stops or
  goes away. A refusal of the request itself (the language server is not ready) ends it with that problem and follows
  nothing. A client that follows is a connected client: the daemon does not stop by itself for being idle while one runs.
- **A first answer of the daemon that was just started is empty**, as for the call with no file: run `gabier start`
  before editing.
- **`gabier mcp` serves no tool for it**: a tool answers once, so the agent calls the tool `affected-tests-changed` after
  each edit ([mcp](mcp.md)).

The daemon method is `watch-affected-tests` (no parameter), which answers like `affected-tests-changed` and then writes
the notifications `affected-tests` (the result of `affected-tests`, never with no test), `refusal` (the error object of
the refusal the stream goes on after), `notice` and `ended`; the protocol is in
[A method that answers more than once](../protocol.md#a-method-that-answers-more-than-once).

## Where the dependencies come from

The dependencies are read in the class files that jdtls compiled, with `java.lang.classfile` of the JDK (no library in
the daemon). The cli asks nothing of them: the daemon tells jdtls of the files that changed on disk, asks for an
incremental build, waits for it, and reads the class folders that jdtls writes for each project (`bin` and `test-bin`
for an Eclipse project, `bin/main` and `bin/test` for Gradle, `target/classes` and `target/test-classes` for Maven)
and the jars of the test classpath of the project (`java.project.getClasspaths` with the scope `test`, and
`java.project.getSettings` for the source folders and the output folders). No `java.*` setting is needed for this. In
a project of several modules, the classes of all the projects jdtls imported are read.

A test is found by the source file name the compiler recorded in its class file and the package of the folder it is in,
so the other top-level types of a file and its nested classes are taken too.

**A Java file that does not compile is refused** (`testing-compilation-failed`, the files named in the problem): jdtls
writes a class for such a file all the same, which is not the class of its text, so the tests it affects cannot be told.
The refusal is made from the errors jdtls published for the files of the project; `gabier diagnostics` tells what they
are. One refused file refuses the whole call.

## The file of filters

`infinitest.filters`, at the root of the project that the daemon serves (one file, none in the folders below), leaves
tests out, as Infinitest reads it: a line is a directive, a pattern is a regular expression that must match the whole
binary name of the class (`demo.LedgerTest$Slow` for a nested class), and the patterns of a line are separated by
commas.

```text
# the integration tests are slow
exclude .*IT
```

- `include <patterns>`: only the classes that match one of them are kept (no `include` line keeps all);
- `exclude <patterns>`, and a line that is no directive, which Infinitest reads as an `exclude`: the classes that match
  are left out;
- a line that starts with `#` or `!` is a comment.

The lines about groups and listeners (`includeGroups`, `excludeGroups`, `testngListeners`, and the older `## groups=`,
`## excluded-groups=`, `## listeners=`) are read and **ignored**: they say how a test runner runs the tests, not which
tests a change affects. A pattern that is not a regular expression is refused (`testing-invalid-test-filter`, with the
file, the line and the pattern).

## What the selection does not see

- **A constant that the compiler inlined, read through a static import**: the test holds the value, not a reference, in
  its class file. A constant read as `Limits.MAX` leaves the class in the class file, with the compiler of jdtls and with
  javac, so that test is selected; `import static app.Limits.MAX` leaves nothing with the compiler of jdtls (ecj), and the
  change of the constant does not reach that test.
- **A class used through reflection or injection**: no reference is in the bytecode.
- **A class linked to a test only through an interface that it implements**: the change of the implementation does not
  select the tests that use the interface.
- **The tests that load classes by themselves** (ArchUnit) cannot be told what they depend on: they are always listed
  when a Java file changed.

## Where this differs from Infinitest

- The annotated methods that make a JUnit 5 test are looked for in the superclasses too.
- An annotation whose class nothing holds (a library that is not on the classpath) is skipped, not taken for the end of
  the search.

## How it is done

The daemon method is `affected-tests` (parameter `files`, absolute paths, one per line); with no file the cli calls
`affected-tests-changed`, which takes no parameter and answers the same result. The daemon asks jdtls and reads
the class folders; the selection (`eu.gabier.testing`, in the core) has no dependency on jdtls and is tested against
class files compiled by the tests, and the replay `AffectedTestsReplayTest` runs the same cases against the real jdtls.
A class file compiled for a Java newer than the one the daemon runs on cannot be read and is refused as
`testing-read-failed`.

## Refusals

A refusal is the problem object on the standard error, exit code 2, and nothing is written. These are the keys the
command can answer, and the help of the command (`gabier help affected-tests`) lists them:

- `testing-compilation-failed`: a file given (or, with no file, a file that changed) has an error the language server
  reported.
- `testing-file-missing`: a Java file given does not exist (never with no file).
- `testing-not-in-a-source-folder`: a Java file given is under no source folder of the project (never with no file).
- `testing-invalid-test-filter`: a pattern of `infinitest.filters` is not a regular expression.
- `testing-read-failed`: a class file, a jar or the file of filters could not be read (a class compiled for a Java newer
  than the daemon's is not read).
- `language-server-request-failed`: the language server failed on the request with an error of its own, which the
  problem relays (its code and its message; the cause is in the log of the daemon, see [the daemon](../daemon.md)).
- `language-server-not-ready`: the Java language server is not ready.
- `already-watching`: with `--watch`, the connection already follows something. The cli follows once per connection, so it
  does not meet it; a client of the protocol does.
- `usage-error`: the arguments are not those of the command (with `--watch`, a file or `--dry-run`).
- `daemon-start-failed`: no daemon answered and `gabier-daemon` could not be started.
- `daemon-connection-failed`: the connection to the daemon was lost, or it hung up without answering.
