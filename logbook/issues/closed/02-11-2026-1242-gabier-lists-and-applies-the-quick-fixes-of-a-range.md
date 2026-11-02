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

## Resolution

Commit bde4081. `bin/ci` (worker, full, once on the worktree): format 10s, build 3s, test 193s, native 165s, jdtls 367s, all passed; coverage 100% after the worker added the missing interruption tests. Reviewed once: nothing blocking.

- Commands (the worker's choice of names): `gabier actions <file> <l:c> <l:c>` and `gabier apply-action [--dry-run] <file> <l:c> <l:c> <title>`, 1-based, end excluded, a point is the same position twice; daemon methods `actions` and `apply-action`. Refusals: `editing-no-such-code-action` (the offered titles in the problem), `editing-ambiguous-code-action` (the choices), `language-server-not-applicable` (a command with no edit, such as "Change signature"); nothing written.
- What gives the behaviour (read in `CodeActionHandler` and `ClientPreferences` at v1.63.0; no `java.*` setting): the standard `textDocument/codeAction` then `codeAction/resolve` (`resolveSupport` for `edit` was already declared). jdtls offers no fix without the diagnostics in `context.diagnostics`, so the adapter opens the file, takes the published diagnostics (same quiet wait as `diagnostics`) and gives those that touch the range. `quickassist` is jdtls's own kind with no LSP4J constant and jdtls drops an action whose kind the client has not declared (`isSupportedCodeActionKind`, by prefix): `JdtlsCapabilities.QUICK_ASSIST` was added ("Convert String concatenation to Text Block" depends on it). `only` is `quickfix`, `refactor`, `quickassist`; the `source.*` actions are file-level, offered on every range, and would make an empty list impossible, so they are left out.
- Decisions: two actions of the same title cannot be told apart (refused with the list; no `--kind` option); each call waits for jdtls to be quiet (5 s), `apply-action` twice; the JSON of `actions` is `{"actions":[{"kind","title"}]}` with no number (the number is the position in the text); an action with no kind is listed with `-`.
- The replay compares the list as "jdtls must offer at least these" and an empty answer exactly; titles stay the text of jdtls (opaque, coupling rule of 02-11-2026).
- Review suggestions: `02-11-2026-1420-actions-review-suggestions.md` (on hold).
- The installed gabier is not rebuilt yet (a pause of the machine was asked at 14:30).

Tokens:
- worker: 457475 (`subagent_tokens`).
- reviewer: 91362 (`subagent_tokens`).
- supervisor: 16 calls, output 12563, input + cache write 23408 (cache read 6152890); covers the supervisor session over the period of the ticket (launch of the worker to now), other work included (the pause, the coupling and seed4j questions); exchanges with the user: 2 messages.
