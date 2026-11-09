# The MCP tools read well to an agent (review findings of PR 36)

Opened: 09-11-2026 13:37

What an agent that calls `diagnostics` on a clean file gets today is a text block with an empty string, which hosts and
the Claude API often reject; and the descriptions of five tools still speak the command line. These are real uses of the
server, so this issue is not on hold.

Done when:
- a `diagnostics` call on a file with no problem answers a non-empty `content` text (`no problems`); the command line
  still prints nothing there; a test drives it through `tools/call`;
- the descriptions of `status`, `move`, `references`, `actions` and `apply-action` (`Operations`) hold no
  command-line word (`--json`, `--include-declaration`, `gabier <command>`): the help of the command line keeps what a
  person needs, through the notes of the command, not those of the operation; a test fails on such a word in any tool
  description;
- an unexpected `RuntimeException` of a call (`internal-server-error`) is logged on stderr with its cause;
- `help mcp` and `docs/commands/mcp.md` agree: `gabier mcp` itself exits only with `usage-error`, the other keys are
  those of a failed call, and `Refusal.NOT_RUNNING` is not worded as "the command never starts a daemon" for `mcp`
  (only `status` starts none); the `Mcp` command is declared in the family that tells the truth;
- `bin/ci` is green.

## Resolution

Commit a4b1711 (PR #37). A `diagnostics` call on a clean file answers `no problems` in `content` (`NO_PROBLEMS`, said by the MCP adapter only; `gabier diagnostics` still prints nothing); the notes of `STATUS`, `MOVE`, `REFERENCES`, `ACTIONS` and `APPLY_ACTION` were rewritten with no command-line word, and `ToolsTest.speaksOfNoWordOfTheCommandLineInTheDescriptionOfAToolOrOfItsParameters` checks every tool; an unexpected failure of a call is logged on stderr with its cause (`ToolCalls.logIfUnexpected`); `help mcp` and `docs/commands/mcp.md` agree on the refusals, with `Command` in a new family (`servesTheOperations`) and its invariant that notes need an operation relaxed so that `mcp` carries notes (two `CommandTest` tests about that invariant were replaced, not weakened).

- `bin/ci` of the worker on 783cf90 (after the fix of the blocking finding): format, build, test, native (it runs `server/discover`, the earlier-era `initialize`, `tools/list`, a `guide` call and a blank `guide` call on the built `gabier mcp`, requires a phrase of the instructions and exactly one warm-up failure line on stderr), jdtls passed; release-install skipped. The first check of PR #37 went red on `StartingJdtlsTest.aWaitThatIsInterruptedIsAConnectionFailureAndKeepsTheInterruption` (untouched by the branch; issue `an-interrupted-wait-test-fails-on-ci-at-random`); the check `bin-ci` on the amended branch is green (10m32s).
- Review (neutral, once): one blocking finding, fixed by the worker in this commit set (the `guide` answer held `--dry-run`, `--token` and `--include-declaration`, which an agent cannot pass to a tool; the rewrite is in `mcp/ToolWords`, with a test over every section of the guide). Suggestions (guide and invalid calls wait for the warm-up, an `Error` in the warm-up thread, `rename-token` missing from the instructions, Strings of `InvalidValuesException`) added to the on-hold issue `mcp-keeps-answering-while-a-call-waits-and-reads-every-id`.
- Process: the worker used `sed` once for one line of a test, against the dogfooding rule.

Tokens:
- worker: 125,174 (cumulative figure of its last completion notification; one resume).
- reviewer: 110,428.
- supervisor: 32 calls, output 20,658, input + cache write 45,649 (cache reads 7,113,525), covering the supervisor session from the launch of the worker (12:44 UTC) to the merge, other work included (the first manual try of `gabier mcp`, the opening of the issues of the findings, the wait of two CI runs); exchanges with the user: 0 messages.
