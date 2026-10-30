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

## Resolution

Merged as `173e106` (third commit of the batch).

Checked by the worker's `bin/ci` (see the first issue of the batch). `ScenariosTest.turnsEveryFileIntoItsSourceWithTheWholeWordOfTheOldNameReplacedByTheNewOne`
applies each rename scenario to its project written in a temporary directory and compares the files with their source
where the whole word of the old name is replaced by the new one, at the new path when a `RenameFile` moves it (the merged
edits of jdtls and the type rename that moves `Greeter.java` included). Mutations shown once and restored:
`RENAME_METHOD` with `replace(5, 18, 24, ...)` (the end swallows the `(`) and with a `newText` "welcomeX" each made only
this test fail. The step 7 of the skill `add-scenario`, which said `ScenariosTest` only checks that the old name is gone,
was updated.

Not reviewed: small ticket (a test, no production code), by the rule of `ship` step 1.

Tokens:
- worker: shared with the batch, see the first issue (124154)
- reviewer: none (not reviewed)
- supervisor: shared with the batch, see the first issue
