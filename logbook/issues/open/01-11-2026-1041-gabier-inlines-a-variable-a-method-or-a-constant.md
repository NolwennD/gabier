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
- [ ] A local variable, a method (called from several places) and a constant are inlined on disk, the declaration removed;
      `--dry-run` writes nothing. Shown on the fake and by one contract scenario per kind replayed on the real jdtls.
- [ ] Refusals, each a scenario replayed on the real jdtls: a position on nothing inlinable, a variable assigned more than once, a
      method that cannot be inlined (recursive or abstract), a file outside the project.
- [ ] `docs/commands/inline.md`, the line of the README list, the row of `docs/protocol.md` and the help of the command (with its
      refusals) are added; `.claude/rules/dogfooding.md` names the command. `bin/ci` green, coverage 100%.
