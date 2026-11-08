# gabier prints the instructions to give a coding agent

Opened: 08-11-2026 10:16

For 0.4.0 (discoverability). The maintainers of a Java project, or the agent itself, run `gabier help agents` and get the
few lines to paste in `AGENTS.md`/`CLAUDE.md`: use `gabier help --for="<task>"` before editing Java by hand, the commands
that replace a text edit, `--dry-run` first, `gabier start` at the start of a session. Today the end of the install prints
one line; nothing says more.

Done when:
- [x] `gabier help agents` prints a short text (about 15 lines, English, plain Markdown) written to be pasted as it is; it
      is a resource of the help, kept with the guide, not a second copy of `docs/`;
- [x] a test fails when a command it cites does not exist or when it cites a command the help does not list; the text
      follows `gabier start` once that ticket is merged;
- [x] `gabier help`, `docs/commands/help.md`, the README ("First steps") and the end message of the install point to it;
      `bin/ci` passes.

Depends on: gabier-start-warms-up-the-daemon (the text cites it).

## Resolution

Done in 61dd5b4 (pull request 28, merged fast-forward). `gabier help agents` prints `agents.md`, 14 lines of plain Markdown
under 120 columns (`modules/cli/src/main/resources/eu/gabier/cli/`, registered in `resource-config.json` for the native
image), written to be pasted as it is: `gabier start` at the start of a session, `gabier help --for=...` before editing
Java by hand, the commands that replace a text edit, `--dry-run` first, diagnostics with actions and apply-action,
`organize-imports`, `gabier help <command>` on a refusal, `gabier stop`. It is the only copy of the text. With `--json` it is
`{"name":"agents","summary":…,"text":…}` like the guide; `help agents <word>` is a usage error; no daemon is needed. The class
`AgentInstructions` (`TOPIC`, `SUMMARY`, `text()`) is the single entry that `init-agent` will call. Tidy in the same
commit: `Guide.read` became the shared `TextResource.read`, its three tests moved to `TextResourceTest`.

- Checked: the worker's full `bin/ci` in one run (format, build, test 209 s, release-install, native 170 s, jdtls 597 s all
  passed; the native stage prints `gabier help agents -> 14 lines`), then `bin-ci` green on the pull request after the rebase
  on the Javadoc fix (run 42486987891, 11 min 7 s).
- Proofs: `AgentInstructionsTest` (printed text, shape, `gabier start` and the help before editing, JSON, usage error, no
  daemon opened); `everyCommandTheTextCitesIsOneTheHelpLists` reads the citations of the text and compares them to
  `Capabilities.all()`, not to a list kept by hand, and `aTextThatCitesACommandTheHelpDoesNotListIsCaught` shows it fails on
  `frobnicate`, `guide` and `agents`. `gabier help`, `docs/commands/help.md`, `docs/cli.md`, the README and the end
  message of the install (`scripts/release-install.sh.template`, `bin/ci.d/35-release-install`) point to it.
- Worker's own words: the new tests were written in the same pass as the code, no red step seen first (the first run failed
  only on a line 121 columns long and a capital letter); coverage is at 100%. Said here so that it is known.
- Review (neutral, once): nothing blocking. Five suggestions in `08-11-2026-1452-help-agents-review-suggestions.md` (on hold): a citation wrapped across
  lines is not checked, the options of cited commands are not checked, a wrong resource-name constant would pass, a
  sentence of `docs/commands/help.md` and a README idiom (`>>` twice appends a second copy). The last two, which are
  wording, were fixed by the supervisor in the closing commit.

Tokens:
- worker: 123724 tokens (last of three notifications of the same run).
- reviewer: 64208 tokens (1 notification).
- supervisor: 17 calls, 11999 output tokens, 31297 input + cache write (4542218 cache reads, apart), from 14:24 to 15:04 on
  08-11-2026 (13:24 to 14:04 UTC); exchanges with the user: 0 messages.
