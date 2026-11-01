# gabier diagnostics

`gabier diagnostics <file>` asks what the Java language server reports in a file, instead of building: one line per
problem, `file:line:column: severity: message`, the file as typed and the line and column **from 1**, as an editor and a
compiler show them (severity: `error`, `warning`, `information` or `hint`), and nothing when the file is clean. The exit
code is 0 whenever the daemon answered, whatever the problems: the reader reads the list. With `--json`, the result is
`{"diagnostics":[{"severity":"error","line":4,"column":15,"message":"..."}]}`, an empty list for a clean file, with the
positions **from 0** as the protocol and the domain count them. A refusal (the file is outside the project, `language-server-file-outside-project`; a path under the project where
there is no file, `language-server-file-missing`; a folder, `language-server-not-a-file`; the server is not ready) is the
problem object, exit code 2. The cli sends the absolute path of the file to the daemon, as typed otherwise, and the daemon alone cleans it: `.` and
`..` are collapsed **lexically** and no symbolic link is followed. So with `src` a link to `/other/src`,
`gabier diagnostics src/../Foo.java` asks for `./Foo.java`, where a shell would open `/other/Foo.java`; type the path
you mean. This is chosen: the folders of the projects are matched as paths, not as files, so resolving the links in the
cli would make a file of a project reached through a link (a `~/work` that links to another disk) look outside it. In text, the file and the message are neutralised for the terminal: see
[Text in a terminal](../cli.md#text-in-a-terminal). The daemon serves the project it was started for (see
[The daemon](../daemon.md)).

```text
$ gabier diagnostics src/main/java/demo/Broken.java
src/main/java/demo/Broken.java:5:16: error: Type mismatch: cannot convert from String to int
$ gabier --json diagnostics src/main/java/demo/Main.java
{"diagnostics":[]}
```

## Refusals

A refusal is the problem object on the standard error, exit code 2. These are the keys the command can answer, and
the help of the command (`gabier help diagnostics`) lists them:

- `language-server-file-outside-project`: the file is not in the project the daemon serves.
- `language-server-file-missing`: the path is under the project and no file is there.
- `language-server-not-a-file`: the path is a folder.
- `language-server-not-ready`: the Java language server is not ready.
- `usage-error`: the arguments are not those of the command.
- `daemon-start-failed`: no daemon answered and `gabier-daemon` could not be started.
- `daemon-connection-failed`: the connection to the daemon was lost, or it hung up without answering.
