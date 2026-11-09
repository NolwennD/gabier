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
