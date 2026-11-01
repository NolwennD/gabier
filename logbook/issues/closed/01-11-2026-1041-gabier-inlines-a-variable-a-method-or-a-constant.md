# gabier inlines a variable, a method or a constant

Opened: 01-11-2026 10:41

An agent replaces a local variable, a method or a constant by its value with `gabier inline`, the counterpart of
`extract-variable` and `extract-method`, instead of editing every use by hand. `gabier inline [--dry-run] <file> <line>:<column>`
(1-based; the symbol under the position: a local variable, a method call or declaration, a constant) inlines every occurrence and
removes the declaration. The answer is the files changed and the unified diff, text and `--json`; a refusal is a problem object,
nothing written. Daemon method (skill `add-daemon-method`) and cli command on the shape of `extract-method`.

**Care**: jdtls is configured, not fought. Start from the Eclipse JDT LS documentation and source (clone at
/home/nolwenn/dev/projects/eclipse.jdt.ls, read-only): the code action `refactor.inline` and the `java/getRefactorEdit` commands for
inlining; write in the resolution which settings or capabilities give the behaviour. Tests first, fake first, then scenarios replayed
on the real jdtls (skill `add-scenario`).

Done when:
- [x] A local variable, a method (called from several places) and a constant are inlined on disk, the declaration removed;
      `--dry-run` writes nothing. Shown on the fake and by one contract scenario per kind replayed on the real jdtls.
- [x] Refusals, each a scenario replayed on the real jdtls: a position on nothing inlinable, a variable assigned more than once, a
      method that cannot be inlined (recursive or abstract), a file outside the project.
- [x] `docs/commands/inline.md`, the line of the README list, the row of `docs/protocol.md` and the help of the command (with its
      refusals) are added; `.claude/rules/dogfooding.md` names the command. `bin/ci` green, coverage 100%.

## Resolution

Commit d1837c5. `gabier inline [--dry-run] <file> <line>:<column>`: port method `JavaLanguageServer.inline`, use case `Inline`, daemon
method `InlineMethod`, cli command and capability, `Request.Inline` / `Answer.Inlined` and 12 contract scenarios (all replayed on the
real jdtls, applied files compiled), native check `inlineWithTheFakeServer`. Docs: `docs/commands/inline.md`, README, `docs/protocol.md`,
`docs/daemon.md`, `dogfooding.md`, skill `verify`.

**Differs from the intro of this issue** (decision of the supervisor, no `Done when` point broken): jdtls decides from the selection.
On a local variable, or on the declaration of a method or a constant, every use is inlined and the declaration removed; on one call or
use of a method or a constant, that one only is inlined and the declaration stays (`RefactorProcessor.getInlineProposal` sets
`setRemoveDeclaration` and `setReplaceAllReferences` from `isDeclarationSelected()`; no setting changes it). gabier keeps jdtls's
behaviour; help, page and README say it; two scenarios pin it (`inline-one-call-of-a-method`, `inline-one-use-of-a-constant`).

jdtls is configured, not fought: no `java.*` setting. The adapter sends `textDocument/codeAction` with an empty range at the position
and `only=[refactor.inline]`, then `codeAction/resolve`; the existing capabilities (`refactor.inline` literal, `resolveSupport` for
`edit`, `dataSupport`) suffice. `only` selects processors, not actions: the adapter keeps an action only if its kind is
`refactor.inline` and its title starts with "Inline " ("Make static" has the same kind; jdtls offers "Change type to 'var'" on a
variable). The title match relies on jdtls's English labels (non-localized `.properties`). One refusal key, `language-server-not-inlinable`:
jdtls gives no action, or one without edit, and no reason, for nothing inlinable, a variable assigned twice, a recursive or abstract
method.

Order of work (worker's report): tests and scenarios before the code, layer by layer; the red seen was mostly a compile error on the
missing symbol; core scenarios first had guessed edits; the adapter was written against the fake jdtls tests, then the replay on the real
jdtls served as the probe (two failures, each turned into a test seen red before its filter: the kind, the title); the cli tests were
seen red (11 failures) before the command existed.

Checked: worker's full `bin/ci` green on the committed tree before the rebase over `.md` commits (format 12s, build 18s, test 150s with
coverage 100% and Checkstyle, native 161s, jdtls 430s). Review (neutral, once): no blocking finding. Fixed by the supervisor in
the docs (README, page, `verify` typo). Left, opened as a ticket on hold: a declaration that nothing uses is listed as a refusal and no
scenario replays it; inlining from a call to inline everything (`textDocument/definition`). Accepted as is: the title filter
(`JdtlsLanguageServer.java:615`), caught at an upgrade by the replay of the success scenarios; one refusal key for a mistyped position and a
real refusal. The cli tests stay in `GabierTest$Inlining`: the split ticket is later.

Tokens:
- worker: 350324 (subagent_tokens)
- reviewer: 78529 (subagent_tokens)
- supervisor: 17 calls, 10464 output, 27030 input + cache write (1896086 cache read) over the supervisor session from the launch of the
  worker (09:41 UTC) to the close, other work included; exchanges with the user: 1 messages
