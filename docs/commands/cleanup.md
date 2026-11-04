# gabier cleanup

`gabier cleanup [--dry-run] <file> <cleanup>...` rewrites a Java file with clean-ups of jdtls instead of editing the text
by hand: for instance `stringConcatToTextBlock` turns a concatenation of string literals into a text block (the escaped
quotes of the literals are plain quotes in the block; jdtls rewrites a concatenation of three literals or more, and
leaves one of two alone). The identifiers are applied **in the order given**, each on the
text the one before left. `--dry-run` (anywhere after the command) writes nothing and shows what would change. The answer
is that of `rename`, for the one file: the text lists `modified <file>` and the unified diff, preceded by `dry run,
nothing written` for a dry run, and says `nothing to change` for a file that has nothing to clean up (not an error); with
`--json` the result is the same `ChangedFiles` object, empty for such a file. A refusal is the problem object, exit code 2,
and nothing is written.

```text
$ gabier cleanup --dry-run src/main/java/demo/Banner.java stringConcatToTextBlock
dry run, nothing written
modified /work/demo/src/main/java/demo/Banner.java

--- /work/demo/src/main/java/demo/Banner.java
+++ /work/demo/src/main/java/demo/Banner.java
@@ ... @@
-        return "Hello, \"world\"!\n"
-                + "Welcome to demo.\n"
-                + "Bye.\n";
+        return """
+            Hello, "world"!
+            Welcome to demo.
+            Bye.
+            """;
...
$ gabier cleanup src/main/java/demo/Plain.java stringConcatToTextBlock
nothing to change
```

**A clean-up applies to the whole file**: every place it can rewrite is rewritten; for one place only, ask
[`gabier actions`](actions.md) for the range of that place, then give the title of the action to
[`gabier apply-action`](apply-action.md); for a concatenation that should be a text block, the action is "Convert String
concatenation to Text Block".

**The identifiers** are those of the clean-ups of jdtls (its `CleanUpRegistry`): `addDeprecated`, `addFinalModifier`,
`addOverride`, `instanceofPatternMatch`, `invertEquals`, `lambdaExpression`, `lambdaExpressionFromAnonymousClass`,
`qualifyMembers`, `qualifyStaticMembers`, `redundantComparisonStatement`, `redundantFallingThroughBlockEnd`,
`redundantIfCondition`, `redundantModifiers`, `redundantSuperCall`, `renameUnusedLocalVariables`,
`stringConcatToTextBlock`, `switchExpression`, `tryWithResource`, `useSwitchForInstanceofPattern` (the enum `CleanUp` of
the core, which the help of the command repeats). jdtls itself only logs an identifier it does not know and answers no
change; gabier refuses it before it asks (`editing-unknown-clean-up`, the parameter `known` lists the identifiers).
`organizeImports`, which jdtls also has as a clean-up, is not offered: it bypasses the checks of
[`organize-imports`](organize-imports.md) (imports jdtls cannot resolve, types several packages declare), so it is
refused as unknown, and its message points to `organize-imports`.

**How jdtls is asked.** jdtls is configured, not fought. The request `java/cleanup` takes only the file; the clean-ups it
runs are those of the setting `java.cleanup.actions`, in the order of its list (`SaveActionHandler.performManualCleanupActions`).
The daemon sets the setting with `workspace/didChangeConfiguration` (the preferences it started jdtls with go along, in
`JdtlsSettings.withCleanUps`), asks `java/cleanup` for the file, and sets the setting back to an empty list, whatever the
answer, so that nothing is run on save (`java.saveActions.cleanup` stays false). The client does not declare the extended
capability `canUseInternalSettings`, which would make jdtls read the clean-ups from the JDT UI preferences of the
project instead. jdtls answers one edit that covers the whole text of the file, and none when no clean-up changes it.

The daemon method is `cleanup` (parameters `file`, `cleanUps`, `dryRun`; the identifiers are given one per line).

## Refusals

A refusal is the problem object on the standard error, exit code 2, and nothing is written. These are the keys the
command can answer, and the help of the command (`gabier help cleanup`) lists them:

- `editing-unknown-clean-up`: an identifier is not a clean-up of the language server; the problem lists the ones it
  knows (`organizeImports` is not one: use `organize-imports`).
- `language-server-file-outside-project`: the file is not in the project the daemon serves.
- `language-server-file-missing`: the path is under the project and no file is there.
- `language-server-not-a-file`: the path is a folder.
- `editing-stale-edit`: the change cannot be applied to the file as it is.
- `language-server-not-ready`: the Java language server is not ready.
- `usage-error`: the arguments are not those of the command.
- `daemon-start-failed`: no daemon answered and `gabier-daemon` could not be started.
- `daemon-connection-failed`: the connection to the daemon was lost, or it hung up without answering.
