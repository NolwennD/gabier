# gabier extract-method

`gabier extract-method <file> <line>:<column> <line>:<column> <name>` turns statements, or an expression, into a method
instead of cutting and pasting text: the selection (the first character, then the position just after the last one, the
end **excluded**) is replaced by a call to a new `private` method called `<name>`, declared right after the method that
holds the selection. jdtls works out the parameters (the local variables and parameters the selection reads) and the
return value (the local variable the selection assigns and the rest of the method uses, or the value of an expression).
Both positions are **from 1**, as `diagnostics` shows them, and are sent to the daemon from 0. `--dry-run` (anywhere after
the command) writes nothing and shows what would change. The answer is that of `extract-variable`, for the one file:
`modified <file>` and the unified diff, preceded by `dry run, nothing written` for a dry run; with `--json`, the same
`ChangedFiles` object.

A refusal is the problem object, exit code 2, and nothing is written:

- `language-server-not-extractable`: the selection is not statements or an expression that can be extracted, such as half
  a statement (from the middle of one to the middle of the next) or an `if` whose branch returns while the code after it
  still runs. It is the refusal of the other three extractions, in the same words: jdtls does not say why, and the
  sentence says no more than that;
- `language-server-invalid-name`: the name is not a Java identifier (`1st`), or another method of the same class already
  has that name **with the parameter types of the extracted method** (the parameters jdtls worked out, such as `int`
  for `doubled(int)`), which would not compile. The same name with other parameter types is an overload and is accepted;
- a file outside the project, a server that is not ready, a change set that cannot be applied to the file as it is.

The refusals above, except the change set that cannot be applied, are contract scenarios replayed on the real jdtls (`ScenarioReplayTest`). That a name with other parameter types is accepted as an overload is tested on the fake language server only.

```text
$ gabier extract-method --dry-run src/main/java/demo/Totals.java 6:9 6:47 printCount
dry run, nothing written
modified /work/demo/src/main/java/demo/Totals.java

--- /work/demo/src/main/java/demo/Totals.java
+++ /work/demo/src/main/java/demo/Totals.java
@@ ... @@
-        System.out.println("count: " + count);
+        printCount(count);
         System.out.println("done");
     }
+
+    private void printCount(int count) {
+        System.out.println("count: " + count);
+    }
```

**How the name is applied.** jdtls cannot take the name from the client: it proposes one of its own when it computes the refactoring, and expects the client to rename the new method. gabier does what a
client does, as `extract-variable` does. It asks `java/getRefactorEdit` with the command `extractMethod` (the refactoring
behind the code action `refactor.extract.method`; it needs no client capability and no setting of `JdtlsSettings` or
`JdtlsCapabilities`), which answers the edit and the command `java.action.rename` with the place of the new name in the
edited text. It applies that edit in memory, opens the edited text in jdtls (it is never written to disk), and
asks `textDocument/documentSymbol` for the methods of that text, to see whether the class already has the name with the
parameters of the new method: jdtls does not decline such a name, its rename answers an edit that leaves two methods of one
signature, so this refusal is **gabier's own check** and not jdtls's. It then asks `textDocument/rename` for the method with
your name, which jdtls checks as for any rename (a name that is not an identifier is declined), closes the text, and
folds the two edits into one on the file as it is on disk. The unwanted name never reaches the file or the diff. When the
name asked for is the one jdtls proposed, the rename and the overlay are skipped.

The daemon method is `extract-method` (parameters `file`, `startLine`, `startColumn`, `endLine`, `endColumn`, from 0,
`name`, `dryRun`).

## Refusals

A refusal is the problem object on the standard error, exit code 2, and nothing is written. These are the keys the
command can answer, and the help of the command (`gabier help extract-method`) lists them:

- `language-server-not-extractable`: the selection is not an expression or statements that can be extracted, and jdtls
  does not say why.
- `language-server-invalid-name`: the name is not a Java identifier, or another method of the class has it with the
  same parameters.
- `language-server-file-outside-project`: the file is not in the project the daemon serves.
- `language-server-file-missing`: the path is under the project and no file is there.
- `language-server-not-a-file`: the path is a folder.
- `editing-stale-edit`: the change cannot be applied to the file as it is.
- `language-server-not-ready`: the Java language server is not ready.
- `usage-error`: the arguments are not those of the command.
- `daemon-start-failed`: no daemon answered and `gabier-daemon` could not be started.
- `daemon-connection-failed`: the connection to the daemon was lost, or it hung up without answering.
