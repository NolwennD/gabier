# gabier move order: review suggestions

Opened: 02-11-2026 17:12
On hold: findings of the review of 5af03a1, none breaks a `Done when` point; the user lifts the hold. The first one is the flake risk of the exact replay if jdtls varies the order of the edits within one file.

Done when:
- [ ] `WorkspaceEdits.java:92` and `ScenarioReplayTest.java:243`: the order of several edits of **one path** is still jdtls's (stable sort), so "same answer every time" and the exact replay of `Answer.MovedToPackage` depend on jdtls giving the edits of a file in the same order at each run (`[Invoice package edit, Invoice import edit]` and `[import edit, package edit]` give two change sets). Either the edits of one path are sorted by position, or merged into one `EditFile`, with a test with the two orders; or `docs/commands/move.md` says that the order within a file is jdtls's.
- [ ] `WorkspaceEditsTest.java:93`: a test puts an edit before the creation of its file in the input and shows that the creation comes first (low priority: it cannot come from `java/move`).
- [ ] `Move.java:64-66`: `file.toString()` is computed once (`var text = ...`); `.claude/skills/add-scenario/SKILL.md:53`: the added line is wrapped at 120 columns.
