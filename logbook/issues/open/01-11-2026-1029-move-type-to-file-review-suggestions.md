# move-type-to-file: the replay proves who refuses, and the refusal hints at a same-name file

Opened: 01-11-2026 10:29
On hold: findings of the review of `01-11-2026-0855-gabier-moves-a-nested-type-to-its-own-file.md`, no `Done when` point broken and no
real use yet (`.claude/rules/workflow.md`, "Value first"); the user lifts the hold.

Done when:
- [ ] The replay of a refusal (`ScenarioReplayTest.java:233-253`) asserts a fragment of jdtls's message in the test (no parsing in
      production), or `docs/commands/move-type-to-file.md` and the Javadoc of `Scenarios.java` word it as "jdtls refuses, reason not
      distinguished". Today `MOVE_A_TOP_LEVEL_TYPE` passes whatever jdtls's reason is.
- [ ] The replay of `MOVE_A_TYPE_TO_A_FILE_THAT_EXISTS` compares the project files before and after: `Totals.java` is unchanged.
- [ ] The message of `language-server-not-movable` (`LanguageServerException.java:58-67`) tells to look for a file of the same name in the
      package; refusing `Shapes.Totals` while `Totals.java` exists says so.
- [ ] `bin/ci` green, coverage 100%.
