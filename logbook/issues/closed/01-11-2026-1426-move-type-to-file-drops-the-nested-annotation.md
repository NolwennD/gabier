# The help of move-type-to-file says that the annotations of the class stay on it

Opened: 01-11-2026 14:26

Found while splitting the cli tests with `gabier move-type-to-file` (friction, `.claude/rules/dogfooding.md`):
`gabier move-type-to-file GabierTest.java 2014:11` made `WithoutADaemonTest` a top-level class that still carries `@Nested` (and its
import). JUnit does not run a top-level class annotated `@Nested`, silently, and a worker removed the annotation by hand in 12 files. Neither
the page of the command nor the help says what becomes of `@Nested`: they only warn that an *inner* `@Nested` class cannot be
instantiated by JUnit once it needs the outer instance. jdtls moves the text as it is and knows nothing of JUnit. Once closed, an
agent knows what happens to `@Nested` when it moves a test class, or the class comes out as a plain top-level class that runs.

Decision of the user (01-11-2026): a special case. The help and the page only say that an annotation of the class stays on it after the move.
No scenario, no gabier step.

Done when:
- [x] The help (`gabier help move-type-to-file`, `gabier move-type-to-file --help`; a test checks it) and
      `docs/commands/move-type-to-file.md` say that the annotations of the class, such as `@Nested`, stay on it after the move.
- [x] `bin/ci --fast` green (format, build, test).

## Resolution

Commit 4e0b4e3, done by the supervisor, without a worker: a few lines and no behaviour (`.claude/rules/workflow.md`, "Very small technical
tasks"). Test first: `HelpFlagTest` checks the new sentence (seen red, then green); the sentence is in the notes of the help of
`move-type-to-file` and a paragraph of its page. What the ticket first aimed at (a scenario, and the annotation dropped or a documented step)
is not done, by decision of the user: the case is particular. The first wording of this issue claimed that the page said the annotation is
dropped; it did not.

Checked: `bin/ci --fast` green (format 11s, build 5s, test 163s with coverage 100%); native and jdtls not run, no code they reach changed
beyond the text of the help. Not reviewed: small ticket, a help text and its page.

Tokens:
- worker: none
- reviewer: none
- supervisor: 12 calls, 4628 output, 16091 input + cache write (2203607 cache read) over the supervisor session from the message of the
  user that decided it (13:37 UTC) to the close, other work included; exchanges with the user: 1 messages
