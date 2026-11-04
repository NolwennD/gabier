# gabier actions

`gabier actions <file> <line>:<column> <line>:<column>` lists what the language server offers on a range: the **fixes** of
the problems the range holds (a type that is not imported, a method to create, an exception to declare) and the
**rewrites** of the code it covers (a concatenation to a text block, an extraction). It is the way to ask jdtls "what can
you do here?" instead of editing the text. To apply one of them, give its title to
[`gabier apply-action`](apply-action.md); to apply a rewrite to every place of a whole file instead of one range, use
[`gabier cleanup`](cleanup.md). Nothing is written. The exit code is 0 whenever the daemon answered.

The range is two positions, each **from 1** as `diagnostics` shows them, the end excluded (as for `extract-variable`); to
ask about one place, give the same position twice. They are sent to the daemon from 0.

```text
$ gabier actions src/main/java/demo/Offers.java 4:5 4:9
1 quickfix Import 'List' (java.util)
2 refactor.extract.variable Extract to local variable -> gabier extract-variable
...
```

One line per action: its **number** in the list (from 1, in the order of the server), its **kind**, its **title**, and
for some a **command of gabier** (below). The kind
is `quickfix` for a fix, `refactor` or `refactor.*` (`refactor.extract.variable`, ...) for a refactoring, `quickassist` for
a rewrite of the code (jdtls's own kind); an action the server gave no kind is listed with `-`. The kind and the title are
the **language server's text**: gabier does not interpret them. The number changes from one call to the next (it follows
what the range offers), so `apply-action` takes the title.

**The command of gabier.** An action whose kind a command of gabier covers is followed by `-> gabier <command>`. The
link is made from the **kind**, never from the title (the title is the server's text, and several actions can share a
command: jdtls offers "Extract to local variable" and "Extract to local variable (replace all occurrences)"):

| Kind of the action | Command | How to call it |
| --- | --- | --- |
| `refactor.extract.variable` | [`extract-variable`](extract-variable.md) | the same file and range, then the name of the variable |
| `refactor.extract.field` | [`extract-field`](extract-field.md) | the same file and range, then the name of the field |
| `refactor.extract.function` | [`extract-method`](extract-method.md) | the same file and range, then the name of the method |
| `refactor.extract.constant` | [`extract-constant`](extract-constant.md) | the same file and range, then the name of the constant |
| `refactor.inline` | [`inline`](inline.md) | the file and one position, on the name to inline |

Prefer the command when the name matters: `apply-action` applies the action as the server proposes it, with the name jdtls
guesses (`String string = ...`), where the command takes the name you ask for. An action with no such command (a fix, a
rewrite such as the text block, "Introduce Parameter...") is listed as before, and applied by its title.

**A range with nothing offered** prints `no actions` and exits with 0; with `--json`, an empty list.

**`--json`** gives the result as the daemon gave it, the actions in the order of the server (the number is the place in
the list, from 1):

```text
$ gabier --json actions src/main/java/demo/Offers.java 4:5 4:9
{"actions":[{"kind":"quickfix","title":"Import 'List' (java.util)"},{"kind":"refactor.extract.variable","title":"Extract to local variable","command":"extract-variable"}]}
```

The member `command` (the name of the command, `extract-variable`) is present only on an action that has one; the other
members are those they were.

In text, every line is neutralised for the terminal, as a title is hostile text: see
[Text in a terminal](../cli.md#text-in-a-terminal). The cli sends the absolute path of the file to the daemon, which alone
cleans it. `--dry-run` is not an option of this command, it is a usage error.

## What is and is not listed

- **Fixes need the problems of the range.** jdtls computes the fixes of a problem only for the diagnostics the request
  carries, so the daemon opens the file in jdtls, takes the diagnostics jdtls publishes for it, and sends those that touch
  the range as the context of the request. That wait is the one of `gabier diagnostics` (jdtls publishes in stages, so the
  daemon waits until it has been quiet for five seconds): an `actions` call is slow for that reason.
- **Only the actions of the range.** The request asks for the kinds `quickfix`, `refactor` and `quickassist`. The `source`
  actions (organize the imports, generate getters and setters, sort the members) are about a whole file, are offered by
  jdtls whatever the range and would fill every answer: they are left out (`organize-imports` does the first).
- **Some actions are commands for an editor.** "Change signature for 'names'" opens a dialog in an editor: jdtls lists it
  with a command and no edit. It is listed here, and refused by `apply-action` as `language-server-not-applicable`.
- **A bare command is not an action.** jdtls answers a command, not an action, only to a client that does not declare the
  kinds; gabier declares them, and a command is not listed.

## How it is done

The daemon method is `actions` (parameters `file`, `startLine`, `startColumn`, `endLine`, `endColumn`, from 0). The
adapter sends the standard LSP request `textDocument/codeAction` (not a jdtls command, so that another server can answer
it) with the range, the diagnostics that touch it and `only` set to `quickfix`, `refactor` and `quickassist`. What gives
jdtls's answer:

- **`context.diagnostics`**: without them `CodeActionHandler` finds no problem and offers no fix.
- **`context.only`**: it names the processors jdtls runs. By default jdtls runs `quickfix`, `refactor`, `quickassist` and
  `source`.
- **The client capability `textDocument.codeAction.codeActionLiteral.codeActionKind.valueSet`** (`JdtlsCapabilities`): jdtls
  keeps an action only when its kind starts with one of the kinds the client declares
  (`ClientPreferences.isSupportedCodeActionKind`), and drops it otherwise. The kind `quickassist` is not one of the
  Language Server Protocol, so LSP4J has no constant for it and the client declares the string `quickassist`; before it did,
  every rewrite such as "Convert String concatenation to Text Block" was dropped.
- **`codeAction.resolveSupport` for `edit`**: jdtls computes the edit of an action when it is chosen, not in the list.

No `java.*` setting is involved. `ScenarioReplayTest` replays the contract scenarios `code-actions-*` against the real
jdtls: what it must offer (it may offer more), and that a blank line offers nothing.

## Refusals

A refusal is the problem object on the standard error, exit code 2, and nothing is written. These are the keys the
command can answer, and the help of the command (`gabier help actions`) lists them:

- `editing-invalid-range`: the range ends before it starts.
- `language-server-file-outside-project`: the file is not in the project the daemon serves.
- `language-server-file-missing`: the path is under the project and no file is there.
- `language-server-not-a-file`: the path is a folder.
- `language-server-not-ready`: the Java language server is not ready.
- `usage-error`: the arguments are not those of the command.
- `daemon-start-failed`: no daemon answered and `gabier-daemon` could not be started.
- `daemon-connection-failed`: the connection to the daemon was lost, or it hung up without answering.
