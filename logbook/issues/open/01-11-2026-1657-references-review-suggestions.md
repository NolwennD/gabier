# references: the declaration by difference and the refusals are replayed on more cases

Opened: 01-11-2026 16:57
On hold: findings of the review of `01-11-2026-1443-gabier-lists-the-references-of-a-symbol.md`, no `Done when` point broken and no real use yet
(`.claude/rules/workflow.md`, "Value first"); the user lifts the hold.

Done when:
- [ ] `--include-declaration` is replayed on the real jdtls for a type (`Ledger` at 2:13), a field and a local variable, not only a method.
- [ ] An overriding method (`Impl.greet()` implementing `Greeter.greet()`) with `--include-declaration`: a scenario pins what jdtls returns, and the
      page says "the declaration" or "the declarations" accordingly.
- [ ] A column just after a name (the `(` of `ledger.deposit(`) is either refused or answered as the name; a scenario and the page say which.
- [ ] The refusals of a JDK type (`String` in `String s;`), a keyword and a literal are replayed on the real jdtls.
- [ ] `bin/ci` green, coverage 100%.
