# gabier rename takes its position as line:column

Opened: 02-11-2026 20:23

Met while working `move-rewrites-references-in-other-modules`: `gabier rename` takes the position as two operands
`<line> <column>`, while every other command and `.claude/rules/dogfooding.md` write `<line>:<column>`. An agent that
writes `gabier rename <file> 125:25 writeFiles` gets a generic `usage-error` that does not say which operand is wrong.

Done when:
- `gabier rename <file> <line>:<column> <new name>` renames as the other commands take their position; the help, `docs/`
  and the rules say so; whether the two-operand form stays is said in the `## Resolution`;
- a malformed position is refused with a message that names the operand and the expected form;
- `bin/ci` passes.

## Resolution

Done in `a7692ef` (batch with `rename-accepts-a-package`, one pull request): `gabier rename <file> <line>:<column>
<new-name>` reads its position with `Arguments.position`, like the other commands; the help, `docs/commands/rename.md`,
`bin/ci.d/40-native` and `.claude/rules/dogfooding.md` use the new form. The two-operand form is removed (only `rename`
used it); `rename A.java 6 19 welcome` and `rename A.java 6 name` are refused with a message that names the position and
the form `<line>:<column>`. The malformed-position message is `UsageException.invalidPosition`, shared by every command that
reads a position (`MalformedPositionTest`, 20 command lines, and `ArgumentsTest`).

Checked: the worker's full `bin/ci` on the final commit, format 15s, build 5s, test 237s, native 197s, jdtls 550s, all
passed; the check `bin-ci` of the pull request passed (12m13s). Review (once, neutral): the old four-operand form still
answered the generic `usage-error`, fixed in this ticket; no other finding on this issue. The worker's own report did not
reach the supervisor (twice); the evidence is its `bin/ci` output and the review, which read the tests.

Tokens:
- worker: 1,283,354 (sum of the 5 completion notifications, shared with `rename-accepts-a-package`; the notifications look
  cumulative, so the figure overstates)
- reviewer: 90,675 (both issues)
- supervisor: 52 calls, output 28,342, input + cache write 72,469 (cache read 4,840,178), over 10:52-12:50 of 03-11-2026,
  other work included; exchanges with the user: 1 messages
