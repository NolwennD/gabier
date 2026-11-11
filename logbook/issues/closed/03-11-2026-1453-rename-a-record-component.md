# gabier rename tells what jdtls says when it fails on a record component

Opened: 03-11-2026 14:53

An agent that renames a component of a record (`gabier rename --dry-run
modules/core/src/main/java/eu/gabier/editing/domain/Location.java 14:29 path`, the component `file` of `record
Location(Path file, Position position)`) gets `internal-server-error`, with no description, and cannot tell what went
wrong. jdtls 1.63.0 fails there, and gabier does not detect it first (decision of the user, 04-11-2026: jdtls is not
fought, `.claude/rules/workflow.md`): the error of jdtls reaches the agent in the shape of every other refusal of the
server, and the limit is written down. The cause is in jdtls, not in gabier: its fork of `RenameSupport` has no case for
a record, and the redirection of JDT UI (Eclipse bug [566507](https://bugs.eclipse.org/bugs/show_bug.cgi?id=566507)) was
never ported (`jdtls-renames-a-record-component`). The log of the cause in the daemon is
`the-daemon-logs-the-cause-of-an-error`.

Done when:
- a rename that jdtls answers with the response error -32603 (a record with a compact constructor, such as `record
  Mark(String label, int rank) { Mark { ... } }`) is refused with a `language-server-` key and the message of jdtls, not
  with `internal-server-error`; the mapping is made for every request of jdtls, not for records; no check of records, no
  `definition` or `semanticTokens` request before a rename; a test with the fake and a scenario observed on the real
  jdtls first (the key is compared, not the message);
- `gabier help rename` and `docs/commands/rename.md` say what jdtls does on a component of a record: with a compact
  constructor it fails (the refusal above); without one it renames the component, its accessor calls and `this.file` and
  leaves the uses by simple name in the body, which no longer compiles (run `gabier diagnostics` after); a position on a
  call of the accessor is declined by `prepareRename`; with an explicit accessor (`public String label() {...}`) jdtls
  renames the accessor as a getter (`label()` becomes `getCaption()`, seen 04-11-2026) and gabier says nothing of it.
  They point to the upstream issue;
- the two observations of a record with no compact constructor are put side by side first, on the same jdtls: the worker
  of `d414620` (pull request 12, closed) saw the uses by simple name left in the body, the worker of
  `a-guide-says-which-problem-each-improvement-solves` saw a plain record, and one with an extra constructor, renamed
  fully; the shape that leaves them is found (an explicit canonical constructor and a compact one fail with -32603);
- the partial rename has no replay (`Effect` compiles the result, which does not compile): the `## Resolution` says so;
- `bin/ci` passes.

## Resolution

Commit 3f29bd5 (pull request 44). `bin/ci` of the worker, full run on the last commit: format passed, build passed, test passed, native passed, jdtls passed, release-install and release-notes skipped (no changed file reaches them); coupling ceilings unchanged (6 / 5 / 11, 162 scenarios). Check of pull request 44 (`bin-ci`): pass.

- Every response error -32603 of jdtls is refused as `language-server-request-failed` with the code and the message of jdtls (`JdtlsWatch.stopped`, for every request: no check of records, no extra request), in the adapter only. Exit code 2 (the refusal is a bad request, the worker's choice). `JdtlsWatchTest`, `JdtlsLanguageServerTest`, `RenameTest.relaysTheFailureOfTheServerOnARecordComponentAndWritesNothing`, `LanguageServerExceptionTest`; two scenarios, `rename-a-component-of-a-record-with-a-compact-constructor` and `...-with-an-explicit-canonical-constructor`, observed on the real jdtls first and replayed (the key is compared, not the message). `gabier help rename`, `docs/commands/rename.md` ("Components of a record"), `docs/improve.md` and the refusals of the 12 operations that send jdtls a request list the key.
- A light workaround of the adapter, allowed by "jdtls is never fought": a failure of jdtls gets the shape of the other refusals. `FakeJdtls`: a scripted InvalidRequest or InternalError, which is a real answer of jdtls, no longer counts as a fake-only failure.
- Cause, in jdtls 1.63.0: its fork of `RenameSupport` has no case for a record component (the redirection of Eclipse bug 566507 was never ported); with a compact or an explicit canonical constructor the constructor parameter is taken for a local variable and `RenameLocalVariableProcessor.createEdits` throws a `NullPointerException`, which jdtls answers as -32603 "Internal error." with the stack trace as data.
- The two earlier observations, side by side, on the same jdtls: both were right. The partial rename depends on the name, not on the shape of the record: `file`, `net`, `time` and `regex` leave `return file;` unrenamed, for a plain class field too; `label`, `name`, `files`, `caption`, `image` rename fully; a record with an extra non-canonical constructor renames fully; an explicit accessor `label()` becomes `getCaption()`. Repeating after 20 s gave the same result.
- The partial rename has no replay: `Effect` compiles the result, which does not compile, and whether jdtls leaves a use depends on the name. The name-dependent case is the issue `jdtls-leaves-uses-of-a-field-named-like-a-package` (on hold).
- Review: no blocking finding. The suggestions are grouped in `11-11-2026-*-failure-log-and-request-failed-review-suggestions.md` (on hold), except the wording of `docs/daemon.md` on what the log holds (it may hold a path or a name), fixed by the supervisor.

Tokens:
- worker: 116793 (one worker for the batch of four issues, three commits)
- reviewer: 125503 (one review of the batch)
- supervisor: 34 calls, output 22845, input + cache write 241562 (cache reads 4118124), over the period of the batch (launch of the worker 11-11-2026 01:47 UTC to the merge), other work included; exchanges with the user: 0 messages
