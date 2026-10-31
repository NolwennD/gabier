# gabier rename

`gabier rename <file> <line> <column> <new-name>` renames the Java symbol at a position, and everything that refers to
it, in every file of the project: it asks the language server what the rename changes and writes it, all or nothing.
`gabier rename --token <file> <identifier> <new-name>` names the symbol by the first occurrence of an identifier in the
file, as a whole word (`greet` is not found in `greeting`; comments and strings are text like any other, so the first
occurrence may be in one of them). `--dry-run` (anywhere after `rename`) writes nothing and shows what would change.
The position is **from 1**, as `diagnostics` shows it, and is sent to the daemon from 0. The text lists the files that
changed, one per line, then a blank line and the unified diff of it all (`diff -u`, three lines of context, the paths
absolute): `modified <file>`, `created <file>`, `renamed <from> -> <to>` (a public type is renamed with its file) and
`deleted <file>`, in that order, preceded by `dry run, nothing written` for a dry run. With `--json`, the result is
`{"created":[...],"renamed":[{"from":"...","to":"..."}],"deleted":[...],"modified":[...],"diff":"..."}`, the paths as
the daemon names them (absolute, normalized) and the diff exact. In text, the paths and the diff are neutralised as
in [Text in a terminal](../cli.md#text-in-a-terminal), so a tab shows as `\u0009` and a backslash as two. A refusal is the problem object, exit code 2: no symbol at the
position (`language-server-no-such-symbol`), a name the server declines (`language-server-invalid-name`), a file outside
the project, a server that is not ready, an identifier that is not in the file (`editing-token-not-found`), and a change
set that cannot be applied to the files as they are (`editing-stale-edit` and the other `editing-` keys): in every case
nothing is written. A rename is asked of jdtls after it was told of what changed on disk since its last request, so a
second rename sees the files the first one wrote.

```text
$ gabier rename --dry-run src/main/java/demo/Greeter.java 6 19 welcome
dry run, nothing written
modified /work/demo/src/main/java/demo/Greeter.java
modified /work/demo/src/main/java/demo/Main.java

--- /work/demo/src/main/java/demo/Greeter.java
+++ /work/demo/src/main/java/demo/Greeter.java
@@ -3,7 +3,7 @@
...
$ gabier rename --token src/main/java/demo/Greeter.java greet welcome
modified /work/demo/src/main/java/demo/Greeter.java
modified /work/demo/src/main/java/demo/Main.java
...
```

The daemon's methods are `rename` (parameters `file`, `line`, `column`, `newName`, `dryRun`) and `rename-token`
(`file`, `token`, `newName`, `dryRun`); `dryRun` is `true` or `false`, and line and column are numbers from 0.
