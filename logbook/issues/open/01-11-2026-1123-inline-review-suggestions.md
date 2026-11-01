# inline: a call inlines everywhere, and the refusal of an unused declaration is replayed

Opened: 01-11-2026 11:23
On hold: findings of the review of `01-11-2026-1041-gabier-inlines-a-variable-a-method-or-a-constant.md`, no `Done when` point broken and
no real use yet (`.claude/rules/workflow.md`, "Value first"); the user lifts the hold.

Done when:
- [ ] `gabier inline` on one call or use of a method or a constant inlines every use and removes the declaration, as on the
      declaration: the daemon resolves the position to the declaration with `textDocument/definition` and inlines from there. A
      scenario replayed on the real jdtls; the help, `docs/commands/inline.md` and the README say it (today they say that one use is
      inlined alone, as jdtls does).
- [ ] The refusal "a declaration that nothing uses" (listed in `Command.java` and `docs/commands/inline.md`) is a scenario replayed on
      the real jdtls for a constant, a local variable and a method, or the claim is dropped for what jdtls does not refuse.
- [ ] `bin/ci` green, coverage 100%.
