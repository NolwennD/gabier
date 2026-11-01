# The cli tests tie their command lists to the capabilities, and the Asserts of Command are shown throwing

Opened: 01-11-2026 14:26
On hold: findings of the review of `31-10-2026-2348-cli-tests-are-split-contracted-and-parameterised.md` and
`01-11-2026-1100-a-command-is-declared-in-its-own-class.md`, no `Done when` point broken and no real use yet
(`.claude/rules/workflow.md`, "Value first"); the user lifts the hold.

Done when:
- [ ] The command lists of `DaemonAnswersTest` (lines 19-27) and `UnknownUsageTest` (lines 15-26) are tied to `Capabilities.all()`: a
      test fails when a capability is added and these lists are not completed.
- [ ] The `Assert`s of the `Command` record (`Command.java:30`) and of `Capabilities.ordered` (`Capabilities.java:48`) are each shown throwing
      by a test: a usage that does not name the command, an example that does not start with `$ gabier `, a blank part, a duplicate name.
- [ ] `bin/ci` green, coverage 100%.
