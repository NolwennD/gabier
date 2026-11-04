# gabier organize-imports

`gabier organize-imports <file>` fixes the imports of a Java file instead of editing the import lines by hand: it adds the
missing ones, removes the unused ones and orders the rest, by asking jdtls (the code action `source.organizeImports`) and
writing its answer. **It writes the imports as the project writes them**: gabier has no convention of its own, it follows
the files of the project that say how (below), and what they do not say is left to the defaults of jdtls. `--dry-run` (anywhere after the
command) writes nothing and shows what would change. The answer is that of `rename`, for the one file: the text lists
`modified <file>` and the unified diff, preceded by `dry run, nothing written` for a dry run, and says `nothing to
change` for a file whose imports are already in order; with `--json` the result is the same `ChangedFiles` object, empty
for such a file, with a `warnings` member when there is a warning to give. A refusal is the problem object, exit code 2, and nothing is written: a server that is not ready, a file
outside the project (`language-server-file-outside-project`), a path under it with no file
(`language-server-file-missing`) or a folder (`language-server-not-a-file`), a change set that cannot be applied to the file
as it is, and the two cases below.

## How the project says it

jdtls reads no file of the project for its imports: it takes three `java.*` settings from the client that talks to it.
The daemon therefore reads the files of the project and gives jdtls those three settings, **before each request that
writes imports** (`organize-imports`, `cleanup`, and the refactorings and code actions that add an import), so a change
of a file is followed by the next request, with no restart. The files are looked for under the root of the project the
daemon serves (the nearest folder with a `.git`, `GABIER_PROJECT`), not in the sub-folders of a module:

| jdtls setting                                      | `.settings/org.eclipse.jdt.ui.prefs` (Eclipse, Buildship) | `.vscode/settings.json` (VS Code Java)             |
| -------------------------------------------------- | -------------------------------------------------------- | -------------------------------------------------- |
| `java.completion.importOrder`                      | `org.eclipse.jdt.ui.importorder` (`\#;java;javax;org;`)   | `"java.completion.importOrder": ["#", "java", ""]` |
| `java.sources.organizeImports.starThreshold`       | `org.eclipse.jdt.ui.ondemandthreshold`                   | `"java.sources.organizeImports.starThreshold": 99` |
| `java.sources.organizeImports.staticStarThreshold` | `org.eclipse.jdt.ui.staticondemandthreshold`             | `"java.sources.organizeImports.staticStarThreshold": 99` |

- `importOrder` is the list of the groups of imports, in order: a group is a package prefix, `#` the static imports and
  the empty group every import that no other group takes (Eclipse writes the groups in one line, separated by `;`, `\#`
  for the static ones, and a trailing `;` for the empty group). jdtls puts a blank line between two groups.
- A threshold is the number of imports of one package (of one type, for the static ones) from which jdtls writes
  `import p.*;` instead. To never write a star import, give the largest `int`, 2147483647.
- `.vscode/settings.json` is JSON with comments (`//`, `/* */`) and trailing commas, as VS Code reads it; its keys are
  the flat names above.
- Each setting comes from the file that sets it. A setting that neither file sets is **left to the defaults of jdtls**:
  the groups `java`, `javax`, `org`, `com` (a blank line between them) and a star import from 99 imports. A project with
  no such file gets those defaults.
- When the two files give a different value for the same setting, **`.settings` is followed** and the answer of the
  command carries a warning (`warning: ...` lines in the text, a `warnings` member in `--json`), also for a file whose
  imports are already in order. The same warning says that a file was ignored because it cannot be read or is not JSON,
  or that a setting has the wrong type.
- `gabier status` shows, for each of the three settings, the value in force and where it comes from (`.settings`,
  `.vscode` or `default of jdtls`); see [status and stop](status-and-stop.md).

This repository declares its convention (the static imports first, a blank line, then one block in ASCII order, as the
formatter and Checkstyle want, and no star import) in a versioned `.vscode/settings.json`: organizing a file of it whose
imports are in order changes nothing.

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

**Which types are candidates.** When a file uses a name with no import, jdtls looks for the types of that name, and
the setting `java.completion.filteredTypes` says which packages it never offers. Its default (`com.sun.*`,
`io.micrometer.shaded.*`, `java.awt.*`, `jdk.*`, `org.graalvm.*`, `sun.*`) holds only when the client sends the setting,
so the daemon sends it, in `JdtlsSettings.javaPreferences`, as `com.sun.*`, `sun.*` and `jdk.*`: the classes of the JDK
that are not meant to be used. `java.awt.*` is left out of the list because it is public: `List` stays a choice between
`java.util.List` and `java.awt.List`, and a project that uses AWT still gets `java.awt.Color`. The filter is by package,
so a class of your own project in a package that starts with `com.sun.` or `sun.` is never a candidate either.

**What jdtls cannot judge, gabier does not write.** Two cases are refused as a problem object (exit code 2), and nothing
is written:

- A type that several packages declare (`Date`: `java.sql.Date`, `java.util.Date`) and that the file uses without
  importing it. jdtls cannot choose and asks the client (`java.action.organizeImports.chooseImports`); the daemon declares
  `advancedOrganizeImportsSupport` and `executeClientCommandSupport` in the `extendedClientCapabilities` it initializes
  jdtls with (`JdtlsCapabilities.extended`), without which jdtls leaves the type unimported and says nothing. gabier
  answers the command that nothing is chosen, and refuses with `language-server-ambiguous-imports`: the parameter
  `candidates` lists the qualified names, those of one name together and in order. Choose, add the import by hand,
  then organize. The candidates are the public ones (see "Which types are candidates" above): `Pattern` is not
  ambiguous, since `com.sun.org.apache.xalan.internal.xsltc.compiler.Pattern` is not offered, and is imported with no
  question.
- An import that jdtls would delete, that the file still uses and that jdtls reports an error on, such as a package of
  a module the file cannot read (`The package java.sql is not accessible` on `import java.sql.*;`, or a class of a source
  set it has no classpath for): jdtls answers an edit that deletes the imports the file uses along with the unused ones.
  The refusal is `language-server-unresolved-imports`, with the parameter `imports` naming them, and the file is not
  touched.

**What the command waits for.** jdtls answers the action at once, and gabier decides from the edit, before writing,
whether doing it loses code. It looks at the imports the edit removes: when none of them is a name the file still uses
(its simple name, `Set` of `java.util.Set`, `entry` of `java.util.Map.entry`, as a word of the file outside the import
lines), nothing that is used can be lost, and the edit is written as it is, with no wait. That is the common case, an
unused import, and it includes an import of a class that does not exist (it was deleted): it is removed. Only when a
removed import is still used does gabier ask the diagnostics of the file, which cost a wait for jdtls to be quiet (about
five seconds), and refuse if jdtls reports an error on that import. A removed star import (`java.sql.*`) has no name to
look for, so it counts as used: the diagnostics are asked, and it is refused when jdtls reports an error on it. An import
of a class that does not exist and that the file uses is kept by jdtls, so it is not a case of the command.

The daemon method is `organize-imports` (parameters `file`,
`dryRun`).

## Refusals

A refusal is the problem object on the standard error, exit code 2, and nothing is written. These are the keys the
command can answer, and the help of the command (`gabier help organize-imports`) lists them:

- `language-server-ambiguous-imports`: several packages declare a type the file uses without importing it; the
  candidates are in the problem.
- `language-server-unresolved-imports`: jdtls would delete imports that the file uses and it reports an error on.
- `language-server-file-outside-project`: the file is not in the project the daemon serves.
- `language-server-file-missing`: the path is under the project and no file is there.
- `language-server-not-a-file`: the path is a folder.
- `editing-stale-edit`: the change cannot be applied to the file as it is.
- `language-server-not-ready`: the Java language server is not ready.
- `usage-error`: the arguments are not those of the command.
- `daemon-start-failed`: no daemon answered and `gabier-daemon` could not be started.
- `daemon-connection-failed`: the connection to the daemon was lost, or it hung up without answering.
