# The guide and its question live below the command line

Opened: 09-11-2026 07:55
Depends on: a-command-runs-an-operation-through-one-entry-point

Step 5 of the plan that prepares the cli for an MCP facade (structure only). `HelpText` both renders the help and works
out the answer to `help --for`; the guide and its ranking sit outside the layers. A `guide` tool of MCP must ask the
same question without the command line.

Done when:
- `Guide`, `GuideSection`, `GuideSections` and the `ranking` package are in `eu.gabier.cli.domain` (the loading of
  the resource stays in an adapter);
- the calculation of the answer to `help --for` is in the application layer, a function of the question and the guide;
  `HelpText` only renders it;
- `gabier help --for="..."` and `gabier help guide` print exactly what they print today;
- the guide tests pass unchanged apart from their package; `docs/cli.md` and `.claude/skills/extend-guide/SKILL.md`
  name the new places;
- `bin/ci` is green.

## Resolution

Commit 97d5ff5 (PR #34). `GuideSection`, `GuideSections`, `ranking` (now `eu.gabier.cli.domain.ranking`) and a new value `Guide(String text)` are in `eu.gabier.cli.domain`; the resource is still loaded in the primary adapter (the old primary class `Guide` became `GuideTopic`, the `improve.md` path is unchanged, so `resource-config.json` is untouched). `AskTheGuide.answer(task, guide)` in the application returns `GuideMatch(rank, section, confidence)`; `HelpText` only writes it, `%.2f` included.

- Output identical: 26 files snapshotted before and after (`help`, `help guide`, `--json help guide`, `help --for` with 9 questions in text and JSON with exit codes, `help <command>`), `diff -r` empty; the same snapshot from the installed `gabier` matches.
- Tests: `AskTheGuideTest`, the domain `GuideTest`; `theSectionsOfTheGuide...` moved from `GuideSectionsTest` to the primary `GuideTest` because it reads the real resource. The other guide tests only swap `Guide` for `GuideTopic`.
- `docs/cli.md`, `docs/commands/help.md` and `.claude/skills/extend-guide/SKILL.md` name the new places.
- Frictions of `gabier move`: `package-info.java` left in the old package; same-package imports left in moved tests (Checkstyle `RedundantImport`); package-private types need `public` by hand (documented in its help).
- `bin/ci` of the worker on the two commits as first made: format, build, test, native, jdtls passed; release-install skipped (no changed file reaches `install.sh`). The worker then rewrote commit 1 to add one skill fix (`.claude/skills/add-daemon-method/SKILL.md`, a `.md` file) and did not rerun `bin/ci`; the check `bin-ci` of PR #34 (13m22s) ran on the final commits and is green.
- Review (neutral, once): nothing blocking. Seven suggestions (a position type of its own, numbers a JSON front end gives, empty list element, the new refusal key `operation-invalid-values` without a command-line test, `kindOf` against `Operations.all()`, a package-private exception that a second adapter cannot catch, order of unknown and missing values) added to the on-hold issue `operation-descriptor-review-suggestions`.

Tokens:
- worker: 282,127 (its completion notification).
- reviewer: 94,858.
- supervisor: 18 calls, output 11,482, input + cache write 31,332 (cache reads 2,527,212), covering the supervisor session from the launch of the worker (08:16 UTC) to the merge, other work included; exchanges with the user: 0 messages.
