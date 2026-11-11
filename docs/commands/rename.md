# gabier rename

`gabier rename <file> <line>:<column> <new-name>` renames the Java symbol at a position (a package too, on its name in a
`package` line: see [Renaming a package](#renaming-a-package)), and everything that refers to it, in every file of the
project: it asks the language server what the rename changes and writes it, all or nothing.
`gabier rename --token <file> <identifier> <new-name>` names the symbol by the first occurrence of an identifier in the
file, as a whole word (`greet` is not found in `greeting`; comments and strings are text like any other, so the first
occurrence may be in one of them). `--dry-run` (anywhere after `rename`) writes nothing and shows what would change. The
position is `<line>:<column>` like every other command's, **from 1**, as `diagnostics` shows it, and is sent to the
daemon from 0 (the former form with two operands, `<line> <column>`, is gone). A position that is not two numbers from 1
joined by a colon is a usage error that names the operand and the form (`Invalid position "6"; expected <line>:<column>,
each a number from 1, as in 12:5`). The text lists the files that changed, one per line, then a blank line and the
unified diff of it all (`diff -u`, three lines of context, the paths absolute): `modified <file>`, `created <file>`,
`renamed <from> -> <to>` (a public type is renamed with its file) and `deleted <file>`, in that order, preceded by `dry
run, nothing written` for a dry run. With `--json`, the result is
`{"created":[...],"renamed":[{"from":"...","to":"..."}],"deleted":[...],"modified":[...],"diff":"..."}`, the paths as
the daemon names them (absolute, normalized) and the diff exact. In text, the paths and the diff are neutralised as in
[Text in a terminal](../cli.md#text-in-a-terminal), so a tab shows as `\u0009` and a backslash as two. A refusal is the
problem object, exit code 2: no symbol at the position (`language-server-no-such-symbol`), a name the server declines
(`language-server-invalid-name`), a package with no class of its own at the position (`language-server-empty-package`), a failure of jdtls on the rename (`language-server-request-failed`, see [Components of a record](#components-of-a-record)), a file outside the project, a server that is not ready, an identifier that is not in
the file (`editing-token-not-found`), and a change set that cannot be applied to the files as they are
(`editing-stale-edit` and the other `editing-` keys): in every case nothing is written. A rename is asked of jdtls after
it was told of what changed on disk since its last request, so a second rename sees the files the first one wrote.

```text
$ gabier rename --dry-run src/main/java/demo/Greeter.java 6:19 welcome
dry run, nothing written
modified /work/demo/src/main/java/demo/Greeter.java
modified /work/demo/src/main/java/demo/Main.java

--- /work/demo/src/main/java/demo/Greeter.java
+++ /work/demo/src/main/java/demo/Greeter.java
@@ -3,7 +3,7 @@
...
$ gabier rename --token src/main/java/demo/Greeter.java greet welcome
modified /work/demo/src/main/java/demo/Greeter.java
modified /work/demo/src/main/java/demo/Main.java
...
```

## Renaming a package

A position on the name in a `package` line (`package demo.app;`) renames the **package**, not a symbol of the file: the
`package` lines of its files, the imports and the qualified names that refer to it, and the folder. Give the whole new
name (`gabier rename src/main/java/demo/app/Greeter.java 1:14 demo.shop`). The files of the package move to the folder
of the new name, which is made when it is missing, and the folders the move leaves empty are removed, up to the source
folder (a dry run removes none). The segment the position is on decides how much of the old name is renamed: on `app`
the package `demo.app`, on `demo` the package `demo`.

jdtls renames the package in **the source folder of the file** only, and gives:

- the sub-packages keep their names and their folders (`demo.app.sub` stays; its imports of `demo.app` are rewritten);
- the same package in another source set (the tests, `src/test/java/demo/app`) keeps its name and its folder, and
  gets an import of the classes that moved. Renaming it too is a second call, on a file of that source set
  (`gabier rename src/test/java/demo/app/GreeterTest.java 1:14 demo.shop`). After the two calls `gabier diagnostics`
  reports no error. The test classes keep the import that the first call gave them, now of their own package, which
  it shows as a warning (`The import demo.shop.Greeter is never used`) and `gabier organize-imports` removes.

A package that exists, or a name that is not a package name, is refused as `language-server-invalid-name`. A position on
a segment whose package has no class of its own in the source folder of the file (`demo` of `package demo.app;` when
every class is in `demo.app` or below) is refused as `language-server-empty-package`: jdtls renames the package named up
to the segment and has nothing to change there. The refusal names that package and the package of the file; to rename the
package of the file, put the position on its last segment and give its whole new name. What stays a
text edit: `module-info.java` (`exports`, `opens`), string literals and comments, and build files.

The name of a module (`demo.records` in `module demo.records {`, in `module-info.java`) is not renamed by jdtls 1.63.0:
it fails with an internal error of its own (a `NullPointerException` on a missing element, response error -32603), and
the refusal is `language-server-request-failed`, with nothing written. Edit the `module` line of the descriptor, and
the `requires` of the other descriptors that name the module, as text; a position on a later segment of the name is
refused as `language-server-no-such-symbol`.

The daemon's methods are `rename` (parameters `file`, `line`, `column`, `newName`, `dryRun`) and `rename-token`
(`file`, `token`, `newName`, `dryRun`); `dryRun` is `true` or `false`, and line and column are numbers from 0.

## Components of a record

jdtls (1.63.0) does not rename a component of a record the way it renames a field, and gabier does not check the record
first: it asks jdtls, and relays what jdtls does. The fork of jdtls of `RenameSupport` has no case for a record
component, and the redirection that JDT UI has for it (Eclipse bug
[566507](https://bugs.eclipse.org/bugs/show_bug.cgi?id=566507)) was not ported to it. What was seen, on the component
`label` of `record Mark(String label, int rank)`:

- with a **compact constructor** (`Mark { ... }`), or an **explicit canonical constructor** (`Mark(String label, int
  rank) { this.label = label; ... }`), jdtls fails: its response error -32603 ("Internal error"; its data is the stack
  trace of a `NullPointerException` of the rename of a local variable, which it takes the parameter of the constructor
  for). The answer is the refusal `language-server-request-failed`, with the code and the message of jdtls, and nothing is
  written (the stack trace is in the log of the daemon, see [the daemon](../daemon.md));
- **without** one of them (a plain record, or one with an extra constructor of another signature), jdtls renames the
  component in the header, its accessor calls (`mark.label()` in the other files) and `this.label`, and the uses of
  `label` by its simple name in the body of the record, for most names. For some names it leaves those uses by simple name
  as they were, and the record no longer compiles (seen with a component named `file`, `net`, `time` and `regex`; a field of
  a class named `file` does the same, so it is not about records): run `gabier diagnostics` on the files of the record
  after a rename. Nothing tells from outside when it happens;
- a position on a **call of the accessor** (`mark.label()`) is declined by jdtls (`prepareRename`), so it is refused as
  `language-server-no-such-symbol`: put the position on the component in the header;
- with an **explicit accessor** (`public String label() { ... }`), jdtls renames the accessor as a getter: `label()`
  becomes `getCaption()`, which is no longer the accessor of the component, and gabier says nothing of it. Rename it back
  by hand.

For a record whose rename fails, edit the header, the constructor and the uses by hand, then run `gabier diagnostics`.

## Refusals

A refusal is the problem object on the standard error, exit code 2, and nothing is written. These are the keys the
command can answer, and the help of the command (`gabier help rename`) lists them:

- `language-server-no-such-symbol`: no symbol at the position.
- `language-server-invalid-name`: the language server declines the new name.
- `language-server-empty-package`: the package named up to the position (a package line) holds no class of its own, so
  there is nothing to rename; put the position on the last segment.
- `editing-token-not-found`: the identifier is not in the file (`--token`).
- `editing-file-missing`: with `--token`, no file is at the path.
- `language-server-file-outside-project`: the file is not in the project the daemon serves.
- `language-server-file-missing`: the path is under the project and no file is there.
- `language-server-not-a-file`: the path is a folder.
- `editing-stale-edit`: the change cannot be applied to the files as they are.
- `language-server-request-failed`: the language server failed on the request with an error of its own, which the
  problem relays (its code and its message; the cause is in the log of the daemon, see [the daemon](../daemon.md)).
- `language-server-not-ready`: the Java language server is not ready.
- `usage-error`: the arguments are not those of the command.
- `daemon-start-failed`: no daemon answered and `gabier-daemon` could not be started.
- `daemon-connection-failed`: the connection to the daemon was lost, or it hung up without answering.
