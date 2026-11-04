# A guide says which problem each improvement solves, shipped with gabier

Opened: 04-11-2026 02:16
Depends on: 04-11-2026-0215-actions-names-the-gabier-command-that-applies-an-action

An agent with a piece of Java finds, in the installed gabier, what can be done to it and why: the language moves (text
blocks, switch expressions, pattern matching: the clean-ups of jdtls) and the structure (an `if` to invert, an `if
return else return` to simplify, which jdtls does not offer). Today the titles of `gabier actions` say what jdtls does,
not which problem it solves, and `gabier help` lists commands, not situations. gabier is used without a clone (README),
so the guide cannot live in `docs/` alone.

Done when:
- [ ] `docs/improve.md` is the one source, read on GitHub and linked from the README; the build copies it into the
  resources of the cli, and the native cli of the release prints it from a folder with no clone (`gabier help guide`,
  the name is chosen in the ticket; `gabier help` points to it).
- [ ] one section per situation: its words, the command or the action, a before/after, its limits. For the language:
  every clean-up of `gabier help cleanup` with the problem it solves. For the structure: what jdtls offers and what it
  does not (invert an `if`, if/return, join and split of conditions: by hand), taken from the source of jdtls. First
  entries from situations met here: a concatenation that is not only literals (`HelpText.java` 55-60: no text block from
  jdtls), one place and not the whole file (`cleanup`), a record component, the package of one source set.
- [ ] the way to extend it is a skill, applied once to add an entry: a situation met with no entry becomes an entry.
  `bin/ci` passes.

## Resolution

Done in `9d83010` (pull request 15).

- `docs/improve.md` is the one source (linked from the README and from `docs/commands/help.md`). The task `copyGuide` of
  `modules/cli/build.gradle.kts` copies it into a generated resource directory of the cli, and
  `META-INF/native-image/eu.gabier/cli/resource-config.json` keeps it in the native image. `gabier help guide` prints it
  (`guide` is a topic of `help`, not a command; with `--json` it is `{"name","summary","text"}`) and `gabier help`
  points to it. Proof: the `native` stage of `bin/ci` (`bin/ci.d/40-native`) runs `help guide` in an empty folder with
  an empty environment and checks three strings; the worker ran its own native build from a folder with no clone.
- One section per situation (`Words:`, `Ask:` or `By hand:`, a before/after, `Limits:`). `GuideTest` checks the
  structure, the 120 columns, and that each clean-up listed by `gabier help cleanup` has a section whose `Ask:` line
  names it (the list is read from the notes of the command; the worker showed the test fail with an added clean-up). The
  first entries come from situations met here: a concatenation that is not only literals (no text block from jdtls), one
  place and not the whole file, a record component, the package of one source set. For the structure, the guide says
  plainly that jdtls 1.63.0 offers no inverting of an `if`, no if/return simplification, no join of conditions (their
  messages are in `CorrectionMessages` and nothing uses them), and that "Invert conditions" only negates the expression.
- `.claude/skills/extend-guide/SKILL.md` describes how to add an entry; it was applied once ("A boolean variable that
  reads the wrong way", Invert local variable).
- `bin/ci` of the worker, after the fix of the review: format, build, test, native and jdtls all passed. The check
  `bin-ci` of the pull request passed (10 min).
- Review (neutral, once), nothing blocking, three suggestions sent back to the worker because they showed that the point
  "the problem each clean-up solves" was not met: two clean-up entries were wrong (`redundantComparisonStatement` and
  `redundantFallingThroughBlockEnd` were said to rewrite nothing; they are now described from the shapes they rewrite),
  the record example showed a complete rename that the guide's own limits deny, and `GuideTest` kept a hand-written copy
  of the clean-up list. The worker amended its commit (`8e9924e` → `9d83010`) and ran `bin/ci` again. Left open, on
  purpose: `Guide` throws an `IllegalStateException` when the resource is missing, which prints `internal-server-error`
  instead of a message naming `improve.md`; the build always copies the file.
- Not as the rules ask: the worker wrote the tests first but ran them after the implementation, so it did not see them
  red, except `GuideTest` after the fix. It used Python for scratch text edits.
- The two entries on the clean-ups rest on the output of `gabier cleanup --dry-run` on a scratch project (`!=` and an
  integer constant only); the source of jdt.core.manipulation was not read by the worker, the reviewer read it.
- Frictions and findings reported, opened or noted: the "Make Static" link of `gabier actions` (shared kind
  `refactor.inline`) and the rewrap of lines, in `actions-command-link-review-suggestions` (hold lifted); "Convert to
  record" never offered, in `actions-offers-convert-to-record`; with an explicit accessor jdtls renames `label()` to
  `getCaption()`, and the two observations of a plain record differ, both in the record issues
  (`rename-a-record-component`, `jdtls-renames-a-record-component`). Not opened, they are behaviours of jdtls that the
  guide states: `renameUnusedLocalVariables` renames only the parameter of a lambda, `gabier actions` offers nothing on
  the name of a class, `no-build-to-import` on a folder with only sources.

Tokens:
- worker: 256703 (`subagent_tokens`, the sum of its two completions)
- reviewer: 130664 (`subagent_tokens`)
- supervisor: 37 calls, 43577 output, 666296 input + cache writes (cache reads: 10794470), from the launch of the worker
  (04-11-2026 03:50) to the closing, with the other work of the session (the issues of the findings, the wait for the
  pull request); exchanges with the user: 1 message
