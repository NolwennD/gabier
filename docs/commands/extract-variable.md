# gabier extract-variable

`gabier extract-variable <file> <line>:<column> <line>:<column> <name>` turns an expression into a local variable instead of
editing the statement by hand: the selection (the first character of the expression, then the position just after its
last one, the end **excluded**) is replaced by `<name>`, and the variable is declared on the line above the statement
that holds it. Both positions are **from 1**, as `diagnostics` shows them, and are sent to the daemon from 0. `--dry-run`
(anywhere after the command) writes nothing and shows what would change. The answer is that of `rename`, for the one file:
`modified <file>` and the unified diff, preceded by `dry run, nothing written` for a dry run; with `--json`, the same
`ChangedFiles` object. A refusal is the problem object, exit code 2, and nothing is written: a selection that is not an
expression or statements that can be extracted, such as a keyword, a whole statement or a call of a `void` method
(`language-server-not-extractable`, the same refusal as the other three extractions, in the same words), a name that is not a Java identifier, or that is the name of a parameter of the method, such as `name` in
`greet(String name)` (`language-server-invalid-name`), a file outside the project, a server that is not ready, a change
set that cannot be applied to the file as it is. The refusals for a keyword, a whole statement, a call of a `void`
method, a name that is not an identifier, the name of a parameter, a file outside the project and a server that is not
ready are contract scenarios replayed on the real jdtls; so is the name `string`, which jdtls proposes for a `String`
and which is accepted without a rename.

**One refusal for the four extractions.** jdtls answers `java/getRefactorEdit` with nothing, and no message, for every
selection it cannot extract (jdtls 1.63: the keyword, the statement and the call of a `void` method here, and the same
selections asked of the other three commands, are scenarios replayed on the real jdtls). The adapter takes the other
shapes of a refusal for the same one (an answer with no edit, with or without an `errorMessage`; a response error
`InvalidRequest`), shown with a fake language server for each command, and the message of jdtls is never carried: the
sentence of the problem is gabier's, the same for the four commands, with the same parameters (`file`, `line`, `column`,
`endLine`, `endColumn`, from 0).

```text
$ gabier extract-variable --dry-run src/main/java/demo/Greeter.java 7:26 7:48 joined
dry run, nothing written
modified /work/demo/src/main/java/demo/Greeter.java

--- /work/demo/src/main/java/demo/Greeter.java
+++ /work/demo/src/main/java/demo/Greeter.java
@@ ... @@
-        String message = greeting + ", " + name;
+        String joined = greeting + ", " + name;
+        String message = joined;
...
```

**How the name is applied.** jdtls cannot take the name from the client: it guesses one (`string` for a `String`) when it
computes the refactoring, and expects the client to rename the new variable. gabier does what a client does. It asks
`java/getRefactorEdit` with the command `extractVariable` (the refactoring behind the code action
`refactor.extract.variable`; it needs no client capability, and replaces the selected expression only, not its other
occurrences), which answers the edit and the command `java.action.rename` with the place of the new name in the edited
text. It applies that edit in memory, opens the edited text in jdtls (it is never written to disk), asks
`textDocument/rename` for the variable with your name, which jdtls checks as for any rename (a name that is not an identifier
is declined), closes the text, and folds the two edits into one on the file as it is on disk. The unwanted name
never reaches the file or the diff. jdtls does not decline the name of a parameter of the method (the case replayed): its
rename answers one edit that corrupts the declaration (`name= ...`, the space before `=` lost). gabier checks that the
rename left the rest of the declaration line as it was, and refuses as `language-server-invalid-name` when it did not;
another collision that jdtls renames cleanly is not caught. The daemon method is `extract-variable` (parameters `file`, `startLine`, `startColumn`,
`endLine`, `endColumn`, from 0, `name`, `dryRun`). The `java.codeGeneration.addFinalForNewDeclaration` setting of jdtls
(`none` by default, which gabier leaves) would make the variable `final`.

## Refusals

A refusal is the problem object on the standard error, exit code 2, and nothing is written. These are the keys the
command can answer, and the help of the command (`gabier help extract-variable`) lists them:

- `language-server-not-extractable`: the selection is not an expression or statements that can be extracted, and jdtls
  does not say why.
- `language-server-invalid-name`: the name is not a Java identifier, or is a parameter of the method.
- `language-server-file-outside-project`: the file is not in the project the daemon serves.
- `language-server-file-missing`: the path is under the project and no file is there.
- `language-server-not-a-file`: the path is a folder.
- `editing-stale-edit`: the change cannot be applied to the file as it is.
- `language-server-request-failed`: the language server failed on the request with an error of its own, which the
  problem relays (its code and its message; the cause is in the log of the daemon, see [the daemon](../daemon.md)).
- `language-server-not-ready`: the Java language server is not ready.
- `usage-error`: the arguments are not those of the command.
- `daemon-start-failed`: no daemon answered and `gabier-daemon` could not be started.
- `daemon-connection-failed`: the connection to the daemon was lost, or it hung up without answering.
