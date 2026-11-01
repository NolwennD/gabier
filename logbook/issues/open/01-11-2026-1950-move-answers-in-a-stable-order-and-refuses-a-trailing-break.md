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
