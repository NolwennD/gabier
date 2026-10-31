# gabier extract-variable

`gabier extract-variable <file> <line>:<column> <line>:<column> <name>` turns an expression into a local variable instead of
editing the statement by hand: the selection (the first character of the expression, then the position just after its
last one, the end **excluded**) is replaced by `<name>`, and the variable is declared on the line above the statement
that holds it. Both positions are **from 1**, as `diagnostics` shows them, and are sent to the daemon from 0. `--dry-run`
(anywhere after the command) writes nothing and shows what would change. The answer is that of `rename`, for the one file:
`modified <file>` and the unified diff, preceded by `dry run, nothing written` for a dry run; with `--json`, the same
`ChangedFiles` object. A refusal is the problem object, exit code 2, and nothing is written: a selection that is not an
expression jdtls can extract (a keyword, a whole statement, a call of a `void` method; `language-server-not-an-expression`),
a name that is not a Java identifier, or that is the name of a parameter of the method, such as `name` in
`greet(String name)` (`language-server-invalid-name`), a file outside the project, a server that is not ready, a change
set that cannot be applied to the file as it is. The refusals for a keyword, a whole statement, a call of a `void`
method, a name that is not an identifier, the name of a parameter, a file outside the project and a server that is not
ready are contract scenarios replayed on the real jdtls; so is the name `string`, which jdtls proposes for a `String`
and which is accepted without a rename.

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
