# gabier move

`gabier move [--dry-run] <file>... <package>` moves Java classes to a package, instead of moving the files by hand and
fixing the `package` lines, the imports and the references. The **last operand is the package**, in dotted form
(`demo.billing`); every operand before it is a file to move, one or several. The package is **created when it does not
exist** (its folder is made under the source folder of the files). `--dry-run` (anywhere after the command) writes
nothing, not even a folder, and shows what would change.

jdtls rewrites the `package` line of each moved file, adds the imports that the moved file now needs (a class it used from
its old package), changes the imports of every file that used it, adds the import to a class of the old package that
used it without one, and moves the file. The answer lists the files that changed (`modified <file>`), the files moved
(`renamed <from> -> <to>`) and the files created, then the unified diff, preceded by `dry run, nothing written` for a dry
run; with `--json`, the `ChangedFiles` object of the other commands that change files.

```text
$ gabier move --dry-run src/main/java/demo/Invoice.java demo.billing
dry run, nothing written
modified /work/demo/src/main/java/demo/Invoice.java
modified /work/demo/src/main/java/demo/Receipt.java
modified /work/demo/src/main/java/demo/app/Shop.java
modified /work/demo/src/test/java/demo/InvoiceTest.java
renamed /work/demo/src/main/java/demo/Invoice.java -> /work/demo/src/main/java/demo/billing/Invoice.java

--- /work/demo/src/main/java/demo/Invoice.java
+++ /work/demo/src/main/java/demo/Invoice.java
@@ ... @@
-package demo;
+package demo.billing;
+
+import demo.Customer;
...
```

**The folders it empties.** A move that leaves the folder of a package empty removes it, and its parents up to the
source folder (`src/main/java`, which stays) when they are empty too: moving the one class of `demo.old.legacy` removes
`demo/old/legacy` and `demo/old`. A folder that still holds a file stays, whatever the file is (a `package-info.java`
that is not moved, a resource). The removal is part of the move: if it fails, the files and the folders are put back as
they were. A dry run removes nothing, and the answer does not list the folders removed.

**Several modules.** The files of the other projects of the build that use a moved class (the modules that require its
module) are rewritten too, imports and qualified names, provided jdtls knows that the projects are linked. jdtls takes the
links from the build: a Gradle build whose plugin `org.gradlex.extra-java-module-info` transforms the jars of its own
projects (its default) gives jdtls no link between the modules, and the move then rewrites the module of the class only.
Such a build sets `skipLocalJars = true` in its `extraJavaModuleInfo` block, as gabier's own does.

**What is rewritten.** The references to a moved class in Java code: the `import` lines, the static imports, and every
fully qualified name (`new demo.Invoice()` becomes `new demo.billing.Invoice()`), in the other packages and in the other
modules of the build. A nested type used through its outer type (`Invoice.Line`) keeps the outer name: only the import
and the package of a qualified name change. The names in a string literal (`"demo.Invoice"`) and in a comment are text,
not references: they are **not** rewritten, and neither is a name in a file that is not Java (a resource, a build file).
Search for the old name in them after the move.

**The order of the imports.** jdtls rewrites the imports of a file without sorting the whole block: the import of a moved
class can land out of the order of the project (observed: `import demo.aaa.Thing;` written between `import
demo.Outer.Success;` and `import demo.Sibling;`, where the order of the project puts it after both). The import
preferences of the project are given to jdtls before the move (as `gabier organize-imports` does, `java.completion.importOrder`
and the thresholds), and no setting of jdtls that gabier knows makes the refactoring sort the block, so the answer is
relayed as jdtls gives it. Run `gabier organize-imports` on the files the move modified to put the imports in the order
of the project (run `gabier diagnostics` on them first: a file whose imports jdtls cannot resolve loses them).

**The module descriptor.** When the move takes the last Java class out of a package (no `.java` file is left in its folder,
a `package-info.java` that stays counts as one), the `exports` and `opens` of that package in the `module-info.java` at the root of
the source folder follow it: they name the new package, with their `to` clause. When the descriptor already has that
directive for the new package, the old one is dropped (a package is exported once). A move that leaves a class in the
package changes nothing in the descriptor, and neither does a package that is not exported or opened. jdtls moves the
files and rewrites what uses them but leaves `module-info.java` as it is (observed on the real server, with no setting
that changes it known), so the daemon makes this edit itself, after jdtls has answered. The directives that name a type (`uses`, `provides`) are not part of this edit. The new package
is not exported or opened by the move: a package that was not, is not.

**After the move.** gabier does not compile the project. A member that was package-private (no `public`) in the old
package and that the moved class uses, or that uses the moved class, is no longer reachable from the other package: jdtls
moves the class all the same, and says nothing about it. Run `gabier diagnostics` on the files that use the moved
classes (and on the moved files) to see it. A class that stays in the old package keeps its other neighbours: only the
files named are moved. Other types of a moved file, and the package-private classes that share its file, move with it.

## How it is done

The command asks jdtls for `java/move` with the move kind `moveResource` (`MoveHandler.moveCU` in the jdtls source): the
files, the folder of the package as the destination, and `updateReferences` true. The answer holds the edits and, for
each file, a `RenameFile` (the client declares the resource operations `create`, `rename` and `delete`, `JdtlsCapabilities`).
The edits come before the rename of their file, so applying the set in order works. jdtls does not answer in the same
order from one run to the next, so the adapter orders the answer: the edits of every file by path (the edits of one file
in the order of jdtls, each applies to the result of the previous ones), then the moves of the files by the path they
leave. The files and the diff of the command, in text and with `--json`, follow that order: the same move gives the same
answer every time. No setting of `JdtlsSettings` is involved.

**What jdtls does not do, gabier does.** jdtls answers **nothing**, with no word, for a package whose folder it does not
know, skips a file it does not know in the middle of a list, moves a file onto a file of its name, and answers a rename
from a path to itself for a file already in the package. So the daemon decides before it asks, with the source folders of
the project (`workspace/executeCommand` `java.project.listSourcePaths`, test sources included): each file must be a Java
source (`.java`) of a source folder, all the files must share that source folder (jdtls would otherwise put a test class
in the main sources), none may be in the package already, and none may take the name of a file of the package or of
another file of the list. The package is taken from the source folder of the files: a test class goes to the test sources.

**The new folders.** jdtls does not see a folder made on disk until it is told, and the protocol has no operation that
makes one. So for a package with no folder the daemon makes the missing folders for the time of the request, tells jdtls
(`workspace/didChangeWatchedFiles`, a `Created` event for the highest new folder, which jdtls refreshes), asks, and removes
the folders it made, telling jdtls again (a `Deleted` event), **whatever the answer, a dry run included**: when the
request ends, the folders are as they were. The change set that comes back moves the files into the folder that does not
exist; applying it makes the folder (`ChangeApplier`: every folder of a destination that does not exist becomes a step
before the move, one per folder, put back if a write fails, the last made first), so the folder exists again, with the
files in it. What is in the way of a folder (a file where a folder of the package would go) cannot be made a folder: the
move is refused with the reason `folder-not-made` and nothing is moved. If the daemon cannot remove a folder it made, it
says so as an internal error, naming the folder.

## Refusals

A refusal is the problem object on the standard error, exit code 2, and nothing is written. These are the keys the
command can answer, and the help of the command (`gabier help move`) lists them:

- `language-server-not-movable-to-package`: the files cannot go to the package. The `reason` parameter says why:
  `already-in-package` (the file is in that package), `name-taken` (a file of that name is in that package, or two files of
  the list have the same name), `not-a-java-source` (the file is not a `.java` file), `outside-source-folders` (the file is
  under no source folder of the project), `several-source-folders` (the files are not all in one source folder),
  `folder-not-made` (the folder of the package cannot be made) and `nothing-to-move` (jdtls has no change to make). The
  `files` parameter names the first file at fault, or all the files when the fault is theirs together.
- `editing-not-a-package-name`: the last operand is not a package name: empty, an empty part (`demo..billing`), a part that
  is not an identifier or is a Java keyword. The default package cannot be the target.
- `language-server-file-outside-project`: a file is not in the project the daemon serves.
- `language-server-file-missing`: the path is under the project and no file is there.
- `language-server-not-a-file`: the path is a folder.
- `editing-file-exists`: a file to create appeared since the language server answered.
- `editing-stale-edit`: the change cannot be applied to the files as they are.
- `language-server-request-failed`: the language server failed on the request with an error of its own, which the
  problem relays (its code and its message; the cause is in the log of the daemon, see [the daemon](../daemon.md)).
- `language-server-not-ready`: the Java language server is not ready.
- `usage-error`: the arguments are not those of the command: fewer than a file and a package, or a file name with a line
  break.
- `daemon-start-failed`: no daemon answered and `gabier-daemon` could not be started.
- `daemon-connection-failed`: the connection to the daemon was lost, or it hung up without answering.

The daemon method is `move` (parameters `files`, the absolute paths, one per line, `package`, and `dryRun`). A package name
that is not one is refused by the daemon as `editing-not-a-package-name`, not as `invalid-parameter`.
