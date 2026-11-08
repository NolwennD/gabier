# The check of the commands cited by gabier help agents sees wrapped citations and options (review suggestions)

Opened: 08-11-2026 14:52
On hold: findings of the review of `gabier-prints-the-instructions-for-an-agent`; none breaks a `Done when` point or a real use met so far.

- `AgentInstructionsTest.java:19`: the pattern matches a literal single space, so a citation wrapped across lines (`gabier` then a newline, then `frobnicate`) is never checked and the test passes when it should fail; use `\\s+` in both places and for the `help` group.
- `AgentInstructionsTest.java:19`: the options of the cited commands are not checked; a cited `gabier help --for=...` or `--dry-run` that stops existing leaves the test green.
- `TextResourceTest.java:18,27`: only the unit test of `TextResource` checks that a name appears in the error of a missing resource; a wrong constant in `Guide` or `AgentInstructions` (`"improve.md"`, `"agents.md"`) would pass, where the old `GuideTest` checked it.
- `docs/commands/help.md:32` says `gabier help` "ends with a line that points to" the instructions; the line is the fourth from last in `HelpText`.
- `README.md:123`: `gabier help agents >> AGENTS.md` appends a second copy when run twice; say "once".

Done when:
- [ ] each point above is either fixed with a test, or answered in this issue's resolution with the reason; `bin/ci` passes.
