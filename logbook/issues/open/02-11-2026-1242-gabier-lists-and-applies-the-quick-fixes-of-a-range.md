# gabier lists and applies the quick fixes of a position or a range

Opened: 02-11-2026 12:42

An agent fixes what the language server can fix, instead of editing text: an error under the cursor (a missing import, a method to create, an
exception to declare), or a rewrite offered on a symbol or on a piece of code (the quick assists, such as "convert to text block"). Two commands
(the names are the worker's to choose, said in the resolution): one lists the code actions available on a position or a range, one applies one of
them. 1-based positions, end excluded, as the other commands. The list gives, for each action, a number, its kind (`quickfix`, `refactor.*`)
and its title (the title is the text of the server: opaque for gabier). The apply command takes the file, the range and the title (the number
changes from one call to the next, the title is the key); it answers the files changed and the unified diff, text and `--json`, `--dry-run`
writes nothing; an unknown title is refused with the titles that exist on this range; an ambiguous one, with the choices; a refusal is a problem
object, nothing written.

**Care**: this is the standard LSP `textDocument/codeAction` and `codeAction/resolve`, not a jdtls command, so that another server can answer
it (the adapter already uses them for `organize-imports` and `inline`); the kinds the client declares are in `JdtlsCapabilities` (the
`quickassist` kind was seen missing). Start with `ripwire` and the JDT LS source at the tag of `mise.toml`, and write in the resolution which
settings and capabilities give the behaviour. The actions that jdtls offers as a *command* with no edit (opening a dialog, choosing a place) are
refused as not applicable, said in the doc. The diagnostics of the range are given to the server as the context of the request (the
adapter has them).

Done when:
- [ ] The list command shows, on a range with an error that jdtls fixes (a type that is not imported) and on a range with a rewrite (a
      concatenation), the actions with kind and title; a range with nothing offered answers an empty list, not an error; shown on the fake
      language server (`GabierTest`, daemon tests on a real socket) and with contract scenarios replayed on the real jdtls (skill `add-scenario`).
- [ ] The apply command applies the action by its title on disk, `--dry-run` writes nothing; an unknown, an ambiguous and a not-applicable action
      are refused as said above, nothing written.
- [ ] `help`, the commands' documents and the dogfooding rule list them; `bin/ci` passes.
