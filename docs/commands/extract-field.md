# gabier extract-field

`gabier extract-field <file> <line>:<column> <line>:<column> <name>` turns an expression into a field of the class instead
of editing the class by hand: the selection (the first character of the expression, then the position just after its last
one, the end **excluded**) is replaced by `<name>`, and a `private` instance field of that name is declared at the top of
the class. Both positions are **from 1**, as `diagnostics` shows them, and are sent to the daemon from 0. `--dry-run`
(anywhere after the command) writes nothing and shows what would change. The answer is that of `extract-variable`, for the
one file: `modified <file>` and the unified diff, preceded by `dry run, nothing written` for a dry run; with `--json`, the
same `ChangedFiles` object.

**Where the field is initialised** is jdtls's choice, and gabier takes its default: **in the current method**, as an
assignment before the statement that holds the expression (`label = "count: ";`). jdtls offers two other places, the field
declaration and the constructors, when the expression allows them; the command takes no option for them.

A refusal is the problem object, exit code 2, and nothing is written:

- `language-server-not-an-expression`: the selection is not an expression jdtls can extract into a field, such as a keyword
  or a whole statement, or it lies outside a class body (the name in the `package` line). jdtls answers nothing, and no
  message, so the refusal is gabier's own sentence and does not tell which of the reasons it is;
- `language-server-invalid-name`: the name is not a Java identifier (`1st`);
- a file outside the project, a server that is not ready, a change set that cannot be applied to the file as it is.

The extraction and the refusals above, except the change set that cannot be applied, are contract scenarios replayed on the
real jdtls (`ScenarioReplayTest`).

```text
$ gabier extract-field --dry-run src/main/java/demo/Totals.java 6:28 6:37 label
dry run, nothing written
modified /work/demo/src/main/java/demo/Totals.java

--- /work/demo/src/main/java/demo/Totals.java
+++ /work/demo/src/main/java/demo/Totals.java
@@ ... @@
+    private String label;
+
     public void print(int[] values) {
         int count = values.length;
-        System.out.println("count: " + count);
+        label = "count: ";
+        System.out.println(label + count);
...
```

**How the name is applied.** jdtls cannot take the name from the client: it guesses one when it computes the refactoring,
and expects the client to rename the new field. gabier does what a client does, as `extract-variable` does. It asks
`java/getRefactorEdit` with the command `extractField` (the refactoring behind the code action `refactor.extract.field`; it
needs no client capability and no setting of `JdtlsSettings` or `JdtlsCapabilities`; its optional first argument, the place
of the initialisation, is not sent), which answers the edit and the command `java.action.rename` with the place of the new
name in the edited text. It applies that edit in memory, opens the edited text in jdtls (it is never written to disk), asks
`textDocument/rename` for the field with your name, which jdtls checks as for any rename (a name that is not an identifier
is declined), closes the text, and folds the two edits into one on the file as it is on disk. The unwanted name never
reaches the file or the diff. When the name asked for is the one jdtls proposed, the rename is skipped. The place jdtls
gives for the name is the assignment, below the declaration, so gabier looks for the line it changed by its number to check
that the rename left it as it was. The daemon method is `extract-field` (parameters `file`, `startLine`, `startColumn`,
`endLine`, `endColumn`, from 0, `name`, `dryRun`).

## Refusals

A refusal is the problem object on the standard error, exit code 2, and nothing is written. These are the keys the
command can answer, and the help of the command (`gabier help extract-field`) lists them:

- `language-server-not-an-expression`: the selection is not an expression jdtls can extract into a field, or it is outside
  a class body.
- `language-server-invalid-name`: the name is not a Java identifier.
- `language-server-file-outside-project`: the file is not in the project the daemon serves.
- `language-server-file-missing`: the path is under the project and no file is there.
- `language-server-not-a-file`: the path is a folder.
- `editing-stale-edit`: the change cannot be applied to the file as it is.
- `language-server-not-ready`: the Java language server is not ready.
- `usage-error`: the arguments are not those of the command.
- `daemon-start-failed`: no daemon answered and `gabier-daemon` could not be started.
- `daemon-connection-failed`: the connection to the daemon was lost, or it hung up without answering.
