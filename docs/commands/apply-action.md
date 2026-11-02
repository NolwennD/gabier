# gabier apply-action

`gabier apply-action [--dry-run] <file> <line>:<column> <line>:<column> <title>` applies one of the actions the language
server offers on a range, chosen by its **title**: the fix of an error (a missing import), or a rewrite of the code (a
concatenation to a text block). The range is the one [`gabier actions`](actions.md) was asked about, and the title one of
those it lists, exactly as written (the number of an action changes from one call to the next, the title is the key). The
positions are **from 1**, the end excluded, sent to the daemon from 0. `--dry-run` (anywhere after the command) writes
nothing and shows what would change.

The answer lists the files that changed (`modified <file>`) and then the unified diff, preceded by `dry run, nothing
written` for a dry run; with `--json`, the `ChangedFiles` object of the other commands that change files.

```text
$ gabier apply-action --dry-run src/main/java/demo/Offers.java 4:5 4:9 "Import 'List' (java.util)"
dry run, nothing written
modified /work/demo/src/main/java/demo/Offers.java

--- /work/demo/src/main/java/demo/Offers.java
+++ /work/demo/src/main/java/demo/Offers.java
@@ ... @@
-package demo;
-
+package demo;
+
+import java.util.List;
+
...
```

## Which action

The daemon asks the server what the range offers, as `actions` does, and takes the action that has the title.

- **No action has this title**: refused as `editing-no-such-code-action`, and the problem lists the actions the range offers
  (`choices`, one `kind: title` per line). Nothing is written.
- **Several actions have this title** (for instance two kinds): refused as `editing-ambiguous-code-action`, and the problem
  lists them. Gabier has no way to tell them apart by title, so ask on a narrower range, which may offer only one.
- **The action is a command for an editor** (it opens a dialog, asks to choose a place): the server gives it with a command
  and no edit, so gabier cannot apply it and refuses it as `language-server-not-applicable`. An action that is offered but
  whose edit changes nothing is refused the same way. Nothing is written.

The title is the language server's text and is not interpreted. The edit is the server's, applied all or nothing like the
edit of every command that changes files; a file that changed since the server answered is refused as `editing-stale-edit`.

## How it is done

The daemon method is `apply-action` (parameters `file`, `startLine`, `startColumn`, `endLine`, `endColumn`, from 0,
`title` and `dryRun`). It asks `textDocument/codeAction` as [`actions`](actions.md) does (the standard LSP request, with the
diagnostics of the range as its context, so that another server can answer it), keeps the action of the same kind and title
and asks `codeAction/resolve` for its edit: the client declares `resolveSupport` for `edit`, so jdtls computes the edit when
the action is chosen. The edit is turned into the change set of the domain and applied. The settings and the capabilities
that give this behaviour are those of [`actions`](actions.md#how-it-is-done). `ScenarioReplayTest` replays the contract
scenarios `apply-*` against the real jdtls.

## Refusals

A refusal is the problem object on the standard error, exit code 2, and nothing is written. These are the keys the
command can answer, and the help of the command (`gabier help apply-action`) lists them:

- `editing-no-such-code-action`: the server offers no action with this title on the range; the problem lists the ones it
  offers.
- `editing-ambiguous-code-action`: several actions have this title on the range; the problem lists them, and a narrower
  range may leave one.
- `language-server-not-applicable`: the action gives no edit (it is a command for an editor, such as a dialog to fill in,
  or it changes nothing).
- `editing-invalid-range`: the range ends before it starts.
- `language-server-file-outside-project`: the file is not in the project the daemon serves.
- `language-server-file-missing`: the path is under the project and no file is there.
- `language-server-not-a-file`: the path is a folder.
- `editing-stale-edit`: the change cannot be applied to the files as they are.
- `language-server-not-ready`: the Java language server is not ready.
- `usage-error`: the arguments are not those of the command.
- `daemon-start-failed`: no daemon answered and `gabier-daemon` could not be started.
- `daemon-connection-failed`: the connection to the daemon was lost, or it hung up without answering.
