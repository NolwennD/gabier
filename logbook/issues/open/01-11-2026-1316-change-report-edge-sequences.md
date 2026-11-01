# The change report is right for a file deleted and recreated, or created and renamed

Opened: 01-11-2026 13:16
On hold: findings of the review of `01-11-2026-1028-a-created-file-is-reported-once-with-one-diff.md`, no `Done when` point broken and no
real use yet (`.claude/rules/workflow.md`, "Value first"); the user lifts the hold.

Done when:
- [ ] A change set `EditFile(X)`, `DeleteFile(X)`, `CreateFile(X)`, `EditFile(X)` on an existing X lists X as created and not as
      modified (`PlanBuilder.create` removes the file from `modified`); a test in `ChangeApplierTest`.
- [ ] A change set `CreateFile(A)`, `RenameFile(A,B)`, `EditFile(B)` reports the file in one way and shows one diff block (today it is
      renamed and modified, with a block from "" to the text); a test.
- [ ] A `CreateFile` alone gives one headers-only block, `created` listed and `modified` empty; a test.
- [ ] `bin/ci` green, coverage 100%.
