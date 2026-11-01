# gabier extract-constant

`gabier extract-constant <file> <line>:<column> <line>:<column> <name>` turns an expression into a constant instead of
editing the class by hand: the selection (the first character of the expression, then the position just after its last
one, the end **excluded**) is replaced by `<name>`, and a `private static final` field of that name, initialised with the
expression, is declared at the top of the class. Both positions are **from 1**, as `diagnostics` shows them, and are sent
to the daemon from 0. `--dry-run` (anywhere after the command) writes nothing and shows what would change. The answer is
that of `extract-variable`, for the one file: `modified <file>` and the unified diff, preceded by `dry run, nothing
written` for a dry run; with `--json`, the same `ChangedFiles` object.

A refusal is the problem object, exit code 2, and nothing is written:

- `language-server-not-extractable`: the selection is not an expression that can be extracted into a constant, such as
  `"count: " + count` when `count` is a local variable, or a keyword. It is the refusal of the other three extractions, in
  the same words: jdtls does not say why, and the sentence says no more than that;
- `language-server-invalid-name`: the name is not a Java identifier (`1ST`);
- a file outside the project, a server that is not ready, a change set that cannot be applied to the file as it is.

The refusals above, except the change set that cannot be applied, are contract scenarios replayed on the real jdtls
(`ScenarioReplayTest`), as is the extraction itself.

```text
$ gabier extract-constant --dry-run src/main/java/demo/Totals.java 6:28 6:37 COUNT_LABEL
dry run, nothing written
modified /work/demo/src/main/java/demo/Totals.java

--- /work/demo/src/main/java/demo/Totals.java
+++ /work/demo/src/main/java/demo/Totals.java
@@ ... @@
+    private static final String COUNT_LABEL = "count: ";
+
     public void print(int[] values) {
         int count = values.length;
-        System.out.println("count: " + count);
+        System.out.println(COUNT_LABEL + count);
...
```

**How the name is applied.** jdtls cannot take the name from the client: it guesses one when it computes the refactoring,
and expects the client to rename the new constant. gabier does what a client does, as `extract-variable` does. It asks
`java/getRefactorEdit` with the command `extractConstant` (the refactoring behind the code action
`refactor.extract.constant`; it needs no client capability and no setting of `JdtlsSettings` or `JdtlsCapabilities`),
which answers the edit and the command `java.action.rename` with the place of the new name in the edited text. It applies
that edit in memory, opens the edited text in jdtls (it is never written to disk), asks `textDocument/rename` for the
constant with your name, which jdtls checks as for any rename (a name that is not an identifier is declined), closes the
text, and folds the two edits into one on the file as it is on disk. The unwanted name never reaches the file or the diff.
When the name asked for is the one jdtls proposed, the rename is skipped. Only the selected expression is replaced, not its
other occurrences. The daemon method is `extract-constant` (parameters `file`, `startLine`, `startColumn`, `endLine`,
`endColumn`, from 0, `name`, `dryRun`).

## Refusals

A refusal is the problem object on the standard error, exit code 2, and nothing is written. These are the keys the
command can answer, and the help of the command (`gabier help extract-constant`) lists them:

- `language-server-not-extractable`: the selection is not an expression or statements that can be extracted, and jdtls
  does not say why.
- `language-server-invalid-name`: the name is not a Java identifier.
- `language-server-file-outside-project`: the file is not in the project the daemon serves.
- `language-server-file-missing`: the path is under the project and no file is there.
- `language-server-not-a-file`: the path is a folder.
- `editing-stale-edit`: the change cannot be applied to the file as it is.
- `language-server-not-ready`: the Java language server is not ready.
- `usage-error`: the arguments are not those of the command.
- `daemon-start-failed`: no daemon answered and `gabier-daemon` could not be started.
- `daemon-connection-failed`: the connection to the daemon was lost, or it hung up without answering.
