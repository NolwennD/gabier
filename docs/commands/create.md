# gabier create

`gabier create <file> <class|interface|record|enum>` creates a Java file that holds an empty type of that kind, instead of
writing it by hand. The name of the type is the name of the file, and the `package` line comes from the folder of the file
in its source folder (a file in the default package has none). The type has no comment (no header, no Javadoc) and an empty
body. `--dry-run` (anywhere after the command) writes nothing and shows the file that would be created.

The answer lists the file created (`created <file>`) and then the unified diff, preceded by `dry run, nothing written` for a
dry run; with `--json`, the `ChangedFiles` object of the other commands that change files.

```text
$ gabier create --dry-run src/main/java/demo/Widget.java class
dry run, nothing written
created /work/demo/src/main/java/demo/Widget.java

--- /dev/null
+++ /work/demo/src/main/java/demo/Widget.java
@@ -0,0 +1,5 @@
+package demo;
+
+public class Widget {
+
+}
```

The four kinds give:

| Kind        | Text of `Widget.java`, `Renderer.java`, `Point.java`, `Color.java`        |
| ----------- | ------------------------------------------------------------------------- |
| `class`     | `package demo;`, a blank line, `public class Widget {`, a blank line, `}` |
| `interface` | the same with `public interface Renderer {`                               |
| `record`    | `package demo;`, a blank line, `public record Point() {`, `}`             |
| `enum`      | the same as the class with `public enum Color {`                          |

The folder of the file must exist: a file is created, a folder is not (see the limit below). The file must not exist: it is
never overwritten.

## How it is done

The command asks jdtls for the text of the file the way an editor gets it for a new file: the file is opened in jdtls with
no text (`textDocument/didOpen`, nothing is written to the disk, and it is closed afterwards), and the completion at its
start (`textDocument/completion`, line 0 column 0) offers the snippets `class`, `interface`, `record` and `enum`
(`SnippetCompletionProposal` in the jdtls source). The snippet of the kind is the text of the file: jdtls writes the
`package` line from the path of the file, the name of the type from its name, and the access `public`. The client declares
that it takes snippets (`completion.completionItem.snippetSupport`, `JdtlsCapabilities`). The adapter takes out of the
snippet what is for an editor: the line of the cursor (`${0}`) in a body, the cursor between the parentheses of a record,
and it ends the file with a line break (`NewTypeText`). The file comes back as a `CreateFile` followed by an edit that
holds its text, applied all or nothing like any change set.

**The comments.** A snippet is written with the code templates of jdtls. By default the type comment is a Javadoc with the
name of the type; gabier starts jdtls with `java.templates.typeComment` set to one blank (`JdtlsSettings.javaPreferences`):
an empty value means the default, a template of white space only evaluates to no text, so the type has no comment. The file
header (`java.templates.fileHeader`) is empty by default, so nothing is set for it. `java.codeGeneration.generateComments`
is false by default and does not govern these snippets. `ScenarioReplayTest` fails if the file jdtls proposes has a comment.
The same setting does not touch the empty Javadoc that `move-type-to-file` writes on the field and the constructor of a
moved inner class: that comment comes from the code templates of the refactoring for a field and a constructor, which have
no setting in jdtls (the replay of `move-an-inner-type-that-uses-the-outer-instance` still shows it).

**The source folders.** jdtls offers the snippet for any path, and for a file under no source folder it writes no `package`
line, which would be a silent wrong answer. So the adapter asks for the source folders of the project
(`workspace/executeCommand` `java.project.listSourcePaths`) and refuses a file under none of them.

**Who refuses.** A file that exists, and a folder that does not, are refused when the change set is applied, before
anything is written. A file name that is not the name of a type (`widget-impl.java`, `1st.java`, a keyword such as
`class.java`) is refused before jdtls is asked. A file outside the project, and a kind that is not one of the four, are
refused with the key below. A kind jdtls has no snippet for (a `record` in a project below Java 14) is refused as
`language-server-not-creatable`, as jdtls does not say why it has none.

**Limit.** gabier creates no folder: a change set only creates files. A new package is a folder to make first (`mkdir -p`),
then `gabier create` gives the right `package` line, as jdtls derives it from the path even when the folder is not there.

The daemon method is `create` (parameters `file`, `kind`, one of `class`, `interface`, `record`, `enum`, and `dryRun`).

## Refusals

A refusal is the problem object on the standard error, exit code 2, and nothing is written. These are the keys the
command can answer, and the help of the command (`gabier help create`) lists them:

- `editing-file-exists`: a file is already there; nothing is overwritten.
- `editing-folder-missing`: the folder of the file does not exist; create it first, gabier creates no folder.
- `editing-not-a-type-name`: the name of the file, without .java, is not the name of a type.
- `language-server-not-creatable`: the file is under no source folder of the project, or the Java level of the project has
  no such type.
- `language-server-file-outside-project`: the file is not in the project the daemon serves.
- `invalid-parameter`: the kind is not class, interface, record or enum.
- `language-server-not-ready`: the Java language server is not ready.
- `usage-error`: the arguments are not those of the command.
- `daemon-start-failed`: no daemon answered and `gabier-daemon` could not be started.
- `daemon-connection-failed`: the connection to the daemon was lost, or it hung up without answering.
