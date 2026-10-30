# The rename scenarios are checked in the core, not only in the replay

Opened: 30-10-2026 17:17

`ScenariosTest.startsEveryRenameOnTheSymbolAndGivesEditsThatStartWhereItIsAndFitTheirFile` replaced a test that asserted
that every edit covers exactly the symbol and has exactly the new name. With `RENAME_METHOD` edited to
`replace(5, 18, 24, "welcome")` (the end swallows the `(`) or a `newText` "welcomeX", no core test fails any more; only
the replay on a real jdtls does, and `check` does not run it.

Done when:
- [ ] A core test applies each rename scenario to its project files and compares each file with its source where the
      whole word of the old name is replaced by the new name (the merged edits of jdtls included).
- [ ] The two mutations above (end swallowing a character; `newText` "welcomeX") each make a core test fail (shown once,
      then restored).
- [ ] `bin/ci` green.
