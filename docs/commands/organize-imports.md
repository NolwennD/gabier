# gabier organize-imports

`gabier organize-imports <file>` fixes the imports of a Java file instead of editing the import lines by hand: it adds the
missing ones, removes the unused ones and orders the rest, by asking jdtls (the code action `source.organizeImports`) and
writing its answer. **It keeps the imports as the project keeps them**: no import is ever collapsed into a star
(`import java.util.*;`), and the order is the one of the formatter and of Checkstyle, the static imports first, a blank
line, then every other import in one block in ASCII order (`java`, `javax` and `org` are not groups of their own), so
that organizing a file of this repository whose imports are in order changes nothing. It is jdtls configured, not
corrected after the fact: the daemon starts it with `java.sources.organizeImports.starThreshold` and
`staticStarThreshold` at the largest `int` (jdtls collapses 99 imports of one package by default) and
`java.completion.importOrder` as `["#", ""]` (the static group, then everything else; the default `java`, `javax`,
`org`, `com` puts a blank line between groups), in `JdtlsSettings.javaPreferences`. `--dry-run` (anywhere after the
command) writes nothing and shows what would change. The answer is that of `rename`, for the one file: the text lists
`modified <file>` and the unified diff, preceded by `dry run, nothing written` for a dry run, and says `nothing to
change` for a file whose imports are already in order; with `--json` the result is the same `ChangedFiles` object, empty
for such a file. A refusal is the problem object, exit code 2, and nothing is written: a server that is not ready, a file
outside the project (`language-server-file-outside-project`), a path under it with no file
(`language-server-file-missing`) or a folder (`language-server-not-a-file`), a change set that cannot be applied to the file
as it is, and the two cases below.

```text
$ gabier organize-imports --dry-run src/main/java/demo/Untidy.java
dry run, nothing written
modified /work/demo/src/main/java/demo/Untidy.java

--- /work/demo/src/main/java/demo/Untidy.java
+++ /work/demo/src/main/java/demo/Untidy.java
@@ ... @@
-import java.util.Set;
...
+import java.util.ArrayList;
+import java.util.Collections;
...
$ gabier organize-imports src/main/java/demo/Tidy.java
nothing to change
```

**What jdtls cannot judge, gabier does not write.** Two cases are refused as a problem object (exit code 2), and nothing
is written:

- A type that several packages declare (`Date`: `java.sql.Date`, `java.util.Date`) and that the file uses without
  importing it. jdtls cannot choose and asks the client (`java.action.organizeImports.chooseImports`); the daemon declares
  `advancedOrganizeImportsSupport` and `executeClientCommandSupport` in the `extendedClientCapabilities` it initializes
  jdtls with (`JdtlsCapabilities.extended`), without which jdtls leaves the type unimported and says nothing. gabier
  answers the command that nothing is chosen, and refuses with `language-server-ambiguous-imports`: the parameter
  `candidates` lists the qualified names, those of one name together and in order. Choose, add the import by hand,
  then organize.
- An import that jdtls would delete and reports an error on, such as a class of a source set it has no classpath for
  (`The package p is not accessible` on the import): jdtls answers an edit that deletes the used imports along with
  the unused ones. The refusal is `language-server-unresolved-imports`, with the parameter `imports` naming them. An
  import that is only unused has a warning, not an error, and is removed as before. The file is not given to jdtls to
  be judged unless the edit removes an import: only then does the request wait for the diagnostics of the file.

The daemon method is `organize-imports` (parameters `file`,
`dryRun`).

## Refusals

A refusal is the problem object on the standard error, exit code 2, and nothing is written. These are the keys the
command can answer, and the help of the command (`gabier help organize-imports`) lists them:

- `language-server-ambiguous-imports`: several packages declare a type the file uses without importing it; the
  candidates are in the problem.
- `language-server-unresolved-imports`: jdtls would delete imports it reports an error on.
- `language-server-file-outside-project`: the file is not in the project the daemon serves.
- `language-server-file-missing`: the path is under the project and no file is there.
- `language-server-not-a-file`: the path is a folder.
- `editing-stale-edit`: the change cannot be applied to the file as it is.
- `language-server-not-ready`: the Java language server is not ready.
- `usage-error`: the arguments are not those of the command.
- `daemon-start-failed`: no daemon answered and `gabier-daemon` could not be started.
- `daemon-connection-failed`: the connection to the daemon was lost, or it hung up without answering.
