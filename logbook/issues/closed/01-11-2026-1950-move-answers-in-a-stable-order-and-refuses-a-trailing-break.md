# gabier move answers in a stable order and refuses a file name with a trailing line break

Opened: 01-11-2026 19:50

Found by the review of `01-11-2026-1835-gabier-moves-classes-to-a-package.md`. Two defects of `gabier move` that an agent meets in use. Once closed, the same
move gives the same answer every time, and a file name is never read as another file.

Done when:
- [ ] The files and the diff of `gabier move` (text and `--json`) come out in a deterministic order, whatever the order of jdtls's answer: the set is
      ordered in the adapter (the edits of a file before its rename, each group by path) or in the plan; `ScenarioReplayTest` asserts that order again for
      `Answer.MovedToPackage` (it compares in any order today, because jdtls's order varied between runs), and a `JdtlsMoveToPackageTest` case gives a shuffled
      answer. `docs/commands/move.md:45` ("edits come before the rename of their file") is then proven.
- [ ] `gabier move $'A.java\n' demo.x` is a usage error: the check of `Move.java:55` (`lines().count() > 1`) ignores a trailing break; it refuses `\n` and `\r`
      anywhere in a name; a test covers a trailing break, a leading one and a lone `\r`.
- [ ] `bin/ci` green, coverage 100%.

## Resolution

Commit 5af03a1. `bin/ci` (worker, full, on the final tree): format 14s, build 4s, test 213s, native 172s, jdtls 397s, all passed, coverage 100% (two earlier runs stopped in `test`: Checkstyle `UnusedLocalVariable` on pattern variables, then JaCoCo on create/delete and on a combined `case A _, B _`; each fixed by a test or a rewrite, not by a threshold). Reviewed once: nothing blocking.

- Order: `WorkspaceEdits.inOrderOfAMove`, called from `JdtlsLanguageServer.askToMove` (the one path that returns a move, dry-run and real): the creations and the edits of every file by path, then the moves by the path they leave, then the removals by path; the edits of one path keep jdtls's order (each applies to the result of the previous ones). `ScenarioReplayTest` now compares `Answer.MovedToPackage` exactly (it compared in any order); the scenarios of `PackageMoveScenarios` are written in that order, which is a property of gabier's answer, not a copy of jdtls's. Tests: `JdtlsMoveToPackageTest.answersTheEditsOfEveryFileByPathThenTheMovesByPathWhateverTheOrderOfJdtls` (a shuffled answer, red before the code), `WorkspaceEditsTest`, `ScenariosTest.listsTheChangesOfAMoveToAPackage...`.
- File name: `Move.java` refuses `\n` and `\r` anywhere (it counted lines, which ignores a trailing break); `MovingToAPackageTest` with a trailing `\n` and `\r` (red before), a leading one and a lone `\r`.
- Known limit (suggestions, `02-11-2026-1712-move-order-review-suggestions.md`, on hold): the order of the edits **within one file** is still jdtls's; if jdtls varies it between runs the exact replay is flaky.

Tokens:
- worker: 138721 (`subagent_tokens`).
- reviewer: 62116 (`subagent_tokens`).
- supervisor: 9 calls, output 13911, input + cache write 22171 (cache read 3912226); covers the supervisor session over the period of the ticket (launch of the worker to now), other work included (the launch of the next worker); exchanges with the user: 0 messages.
