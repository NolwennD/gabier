# gabier diagnostics

`gabier diagnostics <file>...` and `gabier diagnostics --all` ask what the Java language server reports in files, instead of building: one line per
problem, `file:line:column: severity: message`, the files **in the order given**, each as typed, and the line and column
**from 1**, as an editor and a compiler show them (severity: `error`, `warning`, `information` or `hint`), and nothing for a
file that is clean. Give every file you changed in **one call**: the files are asked of the language server together, which
waits for it to be quiet **once** for all of them (the wait is what a request costs, about 5 seconds, see
[the daemon](../daemon.md)), where one call per file pays it each time. A file given twice is answered twice, and opened
once. The exit code is 0 whenever the daemon answered, whatever the problems: the reader reads the list. With `--json`, the
result is `{"files":[{"file":"/work/src/A.java","diagnostics":[{"severity":"error","line":4,"column":15,"message":"..."}]}]}`:
one element per file, in the order given, named by the absolute path the cli sent, with an empty list for a clean file and
the positions **from 0** as the protocol and the domain count them; the shape is the same for one file and for several.
**One file the language server refuses refuses the whole call**: a file that is outside the project
(`language-server-file-outside-project`), a path under the project where there is no file
(`language-server-file-missing`), a folder (`language-server-not-a-file`), or a server that is not ready, is the problem
object, exit code 2, and no file is answered; the problem names the file (`file=...`), and the check is made before any
file is opened. No operand (and no `--all`) is a usage error, and so is `--dry-run` (the command writes nothing, so it has none), and a file
name with a line break (the daemon takes the files one to a line). The cli sends the absolute path of each file to the
daemon, as typed otherwise, and the daemon alone cleans it: `.` and
`..` are collapsed **lexically** and no symbolic link is followed. So with `src` a link to `/other/src`,
`gabier diagnostics src/../Foo.java` asks for `./Foo.java`, where a shell would open `/other/Foo.java`; type the path
you mean. This is chosen: the folders of the projects are matched as paths, not as files, so resolving the links in the
cli would make a file of a project reached through a link (a `~/work` that links to another disk) look outside it. In text, the file and the message are neutralised for the terminal: see
[Text in a terminal](../cli.md#text-in-a-terminal). The daemon serves the project it was started for (see
[The daemon](../daemon.md)). The daemon method is `diagnostics` (parameter `files`: absolute paths, one to a line); `gabier
mcp` serves it as the tool `diagnostics` with `files` as an array.

## The whole project

`gabier diagnostics --all` takes **no file**: it gives the problems of the Java files of the project the daemon serves,
in the format above, in text and with `--json`, and a project with no problem prints nothing, as a clean file does.
The answer is what the language server **published** about the project once it has built it: jdtls compiles the project
incrementally and publishes the problems of every file it builds, the files nobody opened included (checked on the real
jdtls with Gradle, with Maven and with no build tool), so no file is opened, none is read and the time does not grow with
the number of files. The files are the `.java` files under a folder of the project that jdtls published a problem for,
the test sources and any other source set included, and nothing is guessed from the folders. A file with no problem is
**not in the answer**, with `--json` as in text: the result has one element per file that has a problem, in the **order
of the paths**, and `{"files":[]}` for a project with none (the form with files answers a clean file with an empty
list, because it names the file). Each file is named by the **absolute path** the daemon gives it, where the form with
files shows the file as typed. A pom.xml, a file of another directory or anything that is not a `.java` file of the
project is left out and does not refuse the call.

Before it answers, the daemon waits for jdtls to have **finished starting**, which the first call after the start does
(jdtls registers the files it wants watched near the end of its initialization, just before it publishes the problems
of the whole workspace for the first time, and what it answers before that, for a large project, says nothing of the
problems), tells it of the files created, changed or deleted on disk since the last call, asks it for an incremental
build (`java/buildWorkspace`) and waits for the answer, which comes once the build that was running, and the one the
changes call for, are over: **a build that is still running is waited for, not taken for a clean project**, and an edit
made on disk is answered as it is now. The publications of a build are not ordered with that answer, so the daemon then
waits **1 second** in which nothing is published; it is the whole wait of a call once jdtls is started, where the form
with files waits for jdtls to be quiet for 5 seconds. On this checkout (532 Java files, Gradle) the first call, made
right after the start of the daemon, answered after 50 seconds (the start and the import of the project included; opening
every file took 151 seconds), and the next ones after 1.2 seconds. The refusals are a language server that is not ready (`language-server-not-ready`) and one that fails the build with an error
of its own (`language-server-request-failed`). A file, or `--dry-run`, next to `--all` is a usage error. The daemon
method is `diagnostics-all`, with no parameter, and `gabier mcp` serves it as the tool `diagnostics-all`, with no
argument.

```text
$ gabier diagnostics src/main/java/demo/Broken.java
src/main/java/demo/Broken.java:5:16: error: Type mismatch: cannot convert from String to int
$ gabier diagnostics src/main/java/demo/Broken.java src/main/java/demo/Main.java
src/main/java/demo/Broken.java:5:16: error: Type mismatch: cannot convert from String to int
$ gabier --json diagnostics src/main/java/demo/Main.java
{"files":[{"file":"/work/demo/src/main/java/demo/Main.java","diagnostics":[]}]}
$ gabier diagnostics --all
/work/demo/src/main/java/demo/Broken.java:5:16: error: Type mismatch: cannot convert from String to int
/work/demo/src/test/java/demo/BrokenTest.java:5:16: error: Type mismatch: cannot convert from int to String
```

## Refusals

A refusal is the problem object on the standard error, exit code 2. These are the keys the command can answer, and
the help of the command (`gabier help diagnostics`) lists them:

- `language-server-file-outside-project`: the file is not in the project the daemon serves.
- `language-server-file-missing`: the path is under the project and no file is there.
- `language-server-not-a-file`: the path is a folder.
- `language-server-not-ready`: the Java language server is not ready.
- `language-server-request-failed`: with `--all`, the language server failed the build with an error of its own, which the
  problem relays (its code and its message).
- `usage-error`: the arguments are not those of the command.
- `daemon-start-failed`: no daemon answered and `gabier-daemon` could not be started.
- `daemon-connection-failed`: the connection to the daemon was lost, or it hung up without answering.
