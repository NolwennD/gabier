# gabier affected-tests

`gabier [--json] affected-tests <file>...` lists the test classes to run again after a change to Java files, with no
Maven or Gradle and whatever the build tool of the project (Infinitest does it for a developer in an IDE; gabier does it
for an agent). One line per test, the binary name of the class (`demo.LedgerTest`, `demo.Outer$InnerTest`), in the order
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

The daemon method is `affected-tests` (parameter `files`, absolute paths, one per line). The daemon asks jdtls and reads
the class folders; the selection (`eu.gabier.testing`, in the core) has no dependency on jdtls and is tested against
class files compiled by the tests, and the replay `AffectedTestsReplayTest` runs the same cases against the real jdtls.
A class file compiled for a Java newer than the one the daemon runs on cannot be read and is refused as
`testing-read-failed`.

## Refusals

A refusal is the problem object on the standard error, exit code 2, and nothing is written. These are the keys the
command can answer, and the help of the command (`gabier help affected-tests`) lists them:

- `testing-compilation-failed`: a file given has an error the language server reported.
- `testing-file-missing`: a Java file given does not exist.
- `testing-not-in-a-source-folder`: a Java file given is under no source folder of the project.
- `testing-invalid-test-filter`: a pattern of `infinitest.filters` is not a regular expression.
- `testing-read-failed`: a class file, a jar or the file of filters could not be read (a class compiled for a Java newer
  than the daemon's is not read).
- `language-server-request-failed`: the language server failed on the request with an error of its own, which the
  problem relays (its code and its message; the cause is in the log of the daemon, see [the daemon](../daemon.md)).
- `language-server-not-ready`: the Java language server is not ready.
- `usage-error`: the arguments are not those of the command.
- `daemon-start-failed`: no daemon answered and `gabier-daemon` could not be started.
- `daemon-connection-failed`: the connection to the daemon was lost, or it hung up without answering.
