# gabier cleanup says how to change one place only

Opened: 04-11-2026 01:21

`gabier help cleanup` says "Each clean-up rewrites the whole file", and `docs/commands/cleanup.md` says jdtls answers one
edit that covers the whole text, but neither says what to do to change one place only. The way exists: `gabier actions`
on a range, then `gabier apply-action` with a title; the doc of `apply-action` even names "a concatenation to a text
block" as a rewrite of a range. Only that doc leads to it. Seen on eclipse.jdt.ls: to turn a few string concatenations of
a test file into text blocks, `cleanup stringConcatToTextBlock` would have rewritten every concatenation of the file, and
the user had to point to `apply-action`.

Done when:
- [ ] `gabier help cleanup` and `docs/commands/cleanup.md` say in one sentence that a clean-up applies to the whole file,
      and that for one place, `gabier actions` then `gabier apply-action` is the way, with the text block as the example.
- [ ] `docs/commands/actions.md` and `docs/commands/apply-action.md` link back to `cleanup` for the whole file, so that
      each of the three commands names the other two. The tests that check the help and the docs are updated. `bin/ci`
      passes.

## Resolution

Done in `17c97e8` (pull request 13).

- `gabier help cleanup` (the notes of `Cleanup.java`) and `docs/commands/cleanup.md` say that a clean-up applies to the
  whole file and that for one place `gabier actions` then `gabier apply-action` is the way, with the text block
  ("Convert String concatenation to Text Block") as the example. Tests:
  `HelpTest.helpOfCleanupSaysItAppliesToTheWholeFileAndHowToChangeOnePlaceOnly` and
  `CleanupPageLinksTest.thePageOfCleanupSaysHowToChangeOnePlaceOnly`.
- `actions.md` and `apply-action.md` now link to `cleanup.md`, each already linked to the other: the three pages name
  the other two (`CleanupPageLinksTest.eachPageLinksToTheTwoOthers`, for the three pages).
- The five new tests were red before the edits. `bin/ci` of the worker: format (14 s; first run failed on the formatting
  of the new test, fixed with `hk fix`), build, test (591 tests of `:cli`), native and jdtls all passed. The check
  `bin-ci` of the pull request passed (12 min).
- Not reviewed: small ticket (3 lines of production code, a help text; no input parsed, no I/O, no process).
- Not done, on purpose: the help of `actions` and `apply-action` is unchanged, since it already names the commands the
  issue asks for.
- No unrelated bug and no friction with `gabier`: the ticket changed text only, so no Java refactoring was needed.
- The worktree had no issue: it was created from `5b11afc`, the `origin/main` of the launch, while the issue was
  committed on the local `main` only (seven commits ahead, not pushed). The worker fast-forwarded it to `main`. The
  skill `ticket` and `workflow.md` said the worktree starts from the local `main`; they now say `origin/main`, and that
  the supervisor pushes the `.md` commits before it launches a worker.

Tokens:
- worker: 84411 (`subagent_tokens`)
- reviewer: none (not reviewed)
- supervisor: 38 calls, 23471 output, 66320 input + cache writes (cache reads: 9254995), over the period from the launch
  of the worker (04-11-2026 02:31) to the closing, with the other work of the session (the BM25 candidates, the issue
  0217); exchanges with the user: 2 messages
