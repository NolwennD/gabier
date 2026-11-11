# gabier move-type-to-file

`gabier move-type-to-file <file> <line>:<column>` makes a nested type a top-level type in a file of its own, instead of
cutting and pasting text and fixing the imports by hand. The position is anywhere inside the type to move, on its name
or in one of its methods: the **innermost type that encloses it** moves. The position is **from 1**, as `diagnostics`
shows it, and is sent to the daemon from 0. `--dry-run` (anywhere after the command) writes nothing and shows what
would change.

jdtls creates `<Type>.java` in the package of the outer class, takes the type out of the outer class, and updates the
references and the imports of the project (a user of `Shapes.Circle` in another package now imports `demo.Circle`). A
static nested class and an inner class that does not use the outer instance are moved as they are. The answer lists the
files that changed (`modified <file>`) and the one that was created (`created <file>`, listed once, not as modified),
then the unified diff, where the new file is one block (`--- /dev/null`, `+++ <file>`, its text as one hunk), preceded
by `dry run, nothing written` for a dry run; with `--json`, the `ChangedFiles` object of the other commands that change
files.

```text
$ gabier move-type-to-file --dry-run src/main/java/demo/Shapes.java 6:25
dry run, nothing written
modified /work/demo/src/main/java/demo/app/Drawing.java
modified /work/demo/src/main/java/demo/Shapes.java
created /work/demo/src/main/java/demo/Circle.java

--- /work/demo/src/main/java/demo/app/Drawing.java
+++ /work/demo/src/main/java/demo/app/Drawing.java
@@ ... @@
+import demo.Circle;
 import demo.Shapes;
...
--- /dev/null
+++ /work/demo/src/main/java/demo/Circle.java
@@ -0,0 +1,7 @@
+package demo;
+
+public class Circle {
+    public double area(double radius) {
+        return radius * radius * 3;
+    }
+}
\ No newline at end of file
```

## An inner class that uses the outer instance

An inner class (not `static`) that uses the instance of the class that encloses it, a field or a method of the outer
class, cannot live alone: the moved class gets **a field and a constructor parameter of the outer type**, and what it
read is reached through that field (`this.shapes.unit`). A field it reads that was `private` is no longer private. Every
`new Label()` of the project then needs the outer instance.

**A JUnit `@Nested` test class cannot then be instantiated by JUnit**, which needs a constructor without a parameter:
moving such a class breaks the tests. To avoid it, first make the class independent of the outer instance, for example by
having it extend the shared base class (the members it reads from the outer class move to that base class), then move it.
The help of the command (`gabier help move-type-to-file`, `gabier move-type-to-file --help`) says the same.

**The annotations of the class are kept.** jdtls moves the text of the class as it is: an annotation on it, such as
`@Nested`, stays on the new top-level class (with its import). A top-level class still annotated `@Nested` is not run
by JUnit, so remove the annotation if the moved class is a test class that must run on its own.

What jdtls writes in the new file is jdtls's: the file has no line break at its end, which the diff says with
`\ No newline at end of file` (the diff says it only for a file that really lacks the line break), and the field and the
constructor come with an empty Javadoc (`/** * */`). Run the formatter of the project on the new file.

## How it is done

The command asks jdtls for `java/move` with the move kind `moveTypeToNewFile` (the refactoring behind the code action
`refactor.move.moveToNewFile`; `MoveHandler.moveTypeToNewFile` in the jdtls source). The position is sent as an empty
range. The new file comes back as a `CreateFile` followed by an edit that holds its text, which jdtls puts in the answer
only for a client that declares the resource operations `create`, `rename` and `delete`
(`ClientPreferences.isResourceOperationSupported`, `JdtlsCapabilities`). No setting of `JdtlsSettings` is involved. The
empty Javadoc of the field and the constructor is not one that a setting turns off: the preferences that govern
generated comments are already off in jdtls (`java.codeGeneration.generateComments` is false by default, and
`PreferenceManager` sets the JDT preference `CODEGEN_ADD_COMMENTS` to false and passes `createComments = false` to the
refactoring), and the refactoring writes the comment anyway.

**Who refuses.** jdtls refuses everything the command cannot do, with one answer and a message, and gabier gives it one
key. A position outside any type (the `package` line) is answered *no type is selected*; a top-level type, and a nested
type whose name is that of a file already in the package, are answered *a compilation unit named 'X.java' already exists
in package 'p'*. Nothing is overwritten: jdtls refuses before it computes an edit. gabier does not check it again; it only
refuses a change set whose file to create exists when it is applied (`editing-file-exists`, for a file that appeared since
jdtls answered).

The daemon method is `move-type-to-file` (parameters `file`, `line`, `column`, from 0, and `dryRun`).

## Refusals

A refusal is the problem object on the standard error, exit code 2, and nothing is written. These are the keys the
command can answer, and the help of the command (`gabier help move-type-to-file`) lists them:

- `language-server-not-movable`: no nested type can be moved at the position (outside any type, a top-level type, or a
  file of the name of the type exists in its package).
- `language-server-file-outside-project`: the file is not in the project the daemon serves.
- `language-server-file-missing`: the path is under the project and no file is there.
- `language-server-not-a-file`: the path is a folder.
- `editing-file-exists`: the file to create appeared since the language server answered.
- `editing-stale-edit`: the change cannot be applied to the files as they are.
- `language-server-request-failed`: the language server failed on the request with an error of its own, which the
  problem relays (its code and its message; the cause is in the log of the daemon, see [the daemon](../daemon.md)).
- `language-server-not-ready`: the Java language server is not ready.
- `usage-error`: the arguments are not those of the command.
- `daemon-start-failed`: no daemon answered and `gabier-daemon` could not be started.
- `daemon-connection-failed`: the connection to the daemon was lost, or it hung up without answering.
