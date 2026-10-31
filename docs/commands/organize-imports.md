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
outside the project, a change set that cannot be applied to the file as it is.

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

A type that two packages declare (`List`, `Date`) and that the file uses without importing it is **not** imported, and
jdtls does not say so: the file is left with that name unresolved, and `gabier diagnostics` shows it. A file whose
imports jdtls cannot resolve (a class of a source set it has no classpath for) loses them: organize the imports of files
jdtls sees without errors (`gabier diagnostics` first). The daemon method is `organize-imports` (parameters `file`,
`dryRun`).
