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
modified /work/demo/src/test/java/demo/InvoiceTest.java
modified /work/demo/src/main/java/demo/Receipt.java
modified /work/demo/src/main/java/demo/app/Shop.java
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

**After the move.** gabier does not compile the project. A member that was package-private (no `public`) in the old
package and that the moved class uses, or that uses the moved class, is no longer reachable from the other package: jdtls
moves the class all the same, and says nothing about it. Run `gabier diagnostics` on the files that use the moved
classes (and on the moved files) to see it. A class that stays in the old package keeps its other neighbours: only the
files named are moved. Other types of a moved file, and the package-private classes that share its file, move with it.

## How it is done

The command asks jdtls for `java/move` with the move kind `moveResource` (`MoveHandler.moveCU` in the jdtls source): the
files, the folder of the package as the destination, and `updateReferences` true. The answer holds the edits and, for
each file, a `RenameFile` (the client declares the resource operations `create`, `rename` and `delete`, `JdtlsCapabilities`).
The edits come before the rename of their file, so applying the set in order works. No setting of `JdtlsSettings` is
involved.

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
- `language-server-not-ready`: the Java language server is not ready.
- `usage-error`: the arguments are not those of the command: fewer than a file and a package, or a file name with a line
  break.
- `daemon-start-failed`: no daemon answered and `gabier-daemon` could not be started.
- `daemon-connection-failed`: the connection to the daemon was lost, or it hung up without answering.

The daemon method is `move` (parameters `files`, the absolute paths, one per line, `package`, and `dryRun`). A package name
that is not one is refused by the daemon as `editing-not-a-package-name`, not as `invalid-parameter`.
