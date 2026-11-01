# create: type names, snippet line endings and the proofs of the comment setting

Opened: 01-11-2026 15:52
On hold: findings of the review of `01-11-2026-1333-gabier-creates-a-class-an-interface-a-record-or-an-enum.md`, no `Done when` point broken and no
real use yet (`.claude/rules/workflow.md`, "Value first"); the user lifts the hold.

Done when:
- [ ] `TypeNameTest` pins the refusal of a dotted name (`Foo.Bar.java`, `Foo.java.java`).
- [ ] `TypeName` (line 106) refuses a name with an ignorable character (`Foo` + U+200B), or a test fixes the choice.
- [ ] `NewTypeText` (`CURSOR_LINE`) removes the cursor line of a snippet with `\r\n` line endings, and a test covers it.
- [ ] `keepsTheInterruptionOfTheThreadThatWaits` (`JdtlsLanguageServerTest.java:333`) asserts `fake.closed(1)`.
- [ ] The setting `java.templates.typeComment = [" "]` is proven in `check` and not only by the replay of the `jdtls` stage, or the resolution
      says why it cannot be. `bin/ci` green, coverage 100%.
