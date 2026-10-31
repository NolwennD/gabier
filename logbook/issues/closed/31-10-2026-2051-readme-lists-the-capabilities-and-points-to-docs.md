# The README lists the capabilities and points to docs/

Opened: 31-10-2026 20:51

The README was 329 lines and grew with each command; it held the reference of every operation and the instructions for
whoever works on the repository, and it never said what gabier is. After this ticket the README says what gabier is, how to
install it, and lists the **capabilities** (one line each, with a link to its page under `docs/`).

Done when (as redefined with the user on 31-10-2026, after the first worker was stopped):
- [x] `README.md` says what gabier is, how to install it, lists the capabilities with a link each, and is well under half its
      length (30 lines).
- [x] Each capability has a page under `docs/commands/`; the daemon, the protocol (with the table of methods) and the command
      line (with the exit codes) have `docs/daemon.md`, `docs/protocol.md`, `docs/cli.md`.
- [x] What was for whoever works on the repository (setup, hook, `--no-verify`, jdtls) is in `AGENTS.md`, with a pointer to
      `.claude/rules/` and `.claude/skills/`; `CLAUDE.md` is `@AGENTS.md`.
- [x] Nothing is lost, the pointers follow (`scripts/install.sh`, the open issues, the skill `add-daemon-method`).

## Resolution

Done by the supervisor, without a worker, on the user's decision (31-10-2026): only `.md` files change, plus one comment of
`scripts/install.sh` and the Javadoc of `ExitCode`. Merged as `0e1a989`. The first worker was stopped by the user before it reported and left nothing.

The text was moved by line ranges of the old README (a script in the scratchpad, not in the repository), never retyped.
The neutralised-text paragraph, which sat inside the one of `diagnostics`, is now `docs/cli.md`, "Text in a terminal".
Rewritten sentences, all links: "(see below)" in `docs/daemon.md`, "(see above)" in `docs/commands/diagnostics.md`, "neutralised
as above" in `docs/commands/rename.md`. New text: the first paragraph, the install paragraph and the capability lines of the
README, the head of `AGENTS.md`, the table of methods in `docs/protocol.md` (its parameters read from the `parameters()` of
each `DaemonMethod`), and a short pointer from each page.

Proofs: every sentence and every line of an example block of the old README is found in the new README, `AGENTS.md` or
`docs/` (a script of the scratchpad; its eight non-matches are the three rewritten links above and five artefacts of a
sentence split that ran over a heading, checked by hand); every relative link of `README.md`, `AGENTS.md` and `docs/` points
to an existing file; the skill `add-daemon-method` was applied once on a throwaway page (page, README line, table row, link
check), then undone. No `bin/ci` and no test run, on the user's instruction: the only Java change is a Javadoc comment.

The Javadoc of `ExitCode` now holds the content of the exit codes (0 success, 1 internal error, 2 request refused), as
the user asked, and no longer points to the README; it was amended into the commit. The old spec in
`docs/superpowers/` is outside git and was not touched.

Tokens:
- worker: none (stopped by the user before it reported; no figure available)
- reviewer: none (no code, no review)
- supervisor: 67 calls, 67829 output, 756806 input + cache write (cache reads 8828412), exchanges with the user: 3 messages.
  Measured over the session from the opening of the issue (19:51 UTC) to the commit; it includes the planning with the user
  and the questions about the plan.
