# gabier generates the skill or the rule that teaches an agent to use it, as the project's maintainers choose

Opened: 08-11-2026 10:16

For 0.4.0 (discoverability). The maintainers of a Java project choose how their agents learn gabier, and one command
writes it: `gabier init-agent <skill|rule|snippet>` creates `.claude/skills/gabier/SKILL.md`, a rule
(`.claude/rules/gabier.md`) or the block to add to `AGENTS.md`, from the same source as `gabier help agents`. Today
the maintainers copy text by hand and it is out of date at the next version.

Done when:
- [x] the command writes the chosen form in the project (the target folder is an option; a skill has its frontmatter, a rule
      its heading, the snippet a marked block `<!-- gabier:start -->…<!-- gabier:end -->` that a second run replaces); a file
      that exists and that gabier did not write is refused with a key, `--force` overwrites; `--dry-run` prints what it
      would write;
- [x] the content comes from the one source of `gabier-prints-the-instructions-for-an-agent`; the skill and the rule hold
      what the project chooses to include (the commands, with `--only=<command>,…` to cut), and say the version of gabier
      that wrote them;
- [x] a test per form and per refusal on a temporary project; `gabier help init-agent`, `docs/cli.md` and the README say it;
      `bin/ci` passes.

Depends on: gabier-prints-the-instructions-for-an-agent (the source of the text).

## Resolution

Done in 2e5c9d4 (pull request 29, merged fast-forward). `gabier init-agent [--dry-run] [--force] [--target=<folder>]
[--only=<command>,…] <skill|rule|snippet>` is answered by the cli alone (no daemon) and writes the instructions of
`AgentInstructions.text()` (the text of `gabier help agents`, never a copy) in the project: `.claude/skills/gabier/SKILL.md`
(frontmatter `name: gabier` and a one-line `description`, valid plain YAML, a test checks it), `.claude/rules/gabier.md` or
the block to add to `AGENTS.md`. The block is marked `<!-- gabier:start -->` … `<!-- gabier:end -->`, carries the line
`gabier <version> wrote this block (gabier init-agent <form>); run it again after an upgrade`, and a second run replaces it
in place, keeping what is around it. The write goes to a temporary file of the destination folder and is moved atomically;
a link that leads out of the target folder is refused; `--dry-run` writes nothing, not even a folder. Keys, all with the
pointer line of `HelpPointer` except the last: `init-agent-file-exists`, `-block-unfinished`, `-unknown-command`,
`-no-such-folder`, `-outside-folder` (exit 2) and `-write-failed` (exit 1). `docs/commands/init-agent.md` (new),
`gabier help init-agent`, `docs/cli.md`, the README, `docs/commands/help.md` and `docs/protocol.md` say it.

- Decisions: `--only` cuts per bullet of the text: a bullet that cites `gabier <cmd>` stays if one of its commands is in
  `--only`, the lines that cite none always stay, an unknown name is refused (`help` is a valid name; topics are not). A
  `snippet` into an `AGENTS.md` that holds no block is **appended, never refused**: this departs from the letter of the first
  `Done when` point ("a file that exists and that gabier did not write is refused with a key, `--force` overwrites"), which
  stays true for a skill and a rule. The reason: an `AGENTS.md` that exists almost always holds other text, so refusing it
  would make the form useless and `--force` would destroy the maintainer's file; the issue's own words say the snippet is "the
  block to add to `AGENTS.md`". The reviewer agrees. A skill or a rule with no block of gabier is refused, `--force`
  overwrites it.
- Checked: the worker's full `bin/ci` in one run on the final tree (format 19 s, build 5 s, test 253 s, native 180 s, jdtls
  656 s passed; release-install skipped, no changed file reaches it), and `bin-ci` green on the pull request (run
  42490865180, 11 min 22 s). Proofs: `InitAgentTest`, 63 tests on a temporary project folder (each form, the second run, the
  append, the refusals and their keys, `--force`, `--dry-run` in text and JSON, `--only`, the links, no temporary file left
  after a success, write failures, no daemon opened), `CommandRefusalsTest` (the help of the command against its page), and
  the native stage, which runs the binary on a temporary project (snippet created, replaced in place, `--only=rename rule`, a
  dry run of a skill that writes nothing, `--only=frobnicate` refused).
- Method, said by the worker and recorded here: the test list was written as one file in one go, not one test at a time; 52
  of 62 tests failed before any code, for the right reason, and the 10 that passed on a vacuous assertion were made exact
  (then 62 of 63 red); the code was written afterwards in one pass. The new Java files were written with the file tool, not
  with `gabier create`, and their imports by hand (`hk fix --all` ran), against `.claude/rules/dogfooding.md`.
- Review (neutral, once): nothing blocking. Nine suggestions in `08-11-2026-1540-init-agent-review-suggestions.md` (on
  hold): the mode of an existing file is lost on overwrite, a second block or a marker in a code fence is not handled, CRLF
  files get mixed line endings, a gap between the existence check and the move, a skill that lost its frontmatter, tests
  that do not prove "no temporary file left after a failure", a hollow parameterized case, the cut per bullet to say in the
  help, three missing link cases.
- Not done: the install end message still says "paste what `gabier help agents` prints", which stays true; it could mention
  `init-agent` later (it would touch the text pinned by `35-release-install`).

Tokens:
- worker: 201884 tokens (last of several notifications of the same run).
- reviewer: 82929 tokens (1 notification).
- supervisor: 13 calls, 11800 output tokens, 25178 input + cache write (3754958 cache reads, apart), from 15:05 to 15:51 on
  08-11-2026 (14:05 to 14:51 UTC); exchanges with the user: 0 messages.
