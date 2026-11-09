# The MCP server answers questions to the guide

Opened: 09-11-2026 12:08
Depends on: gabier-mcp-carries-instructions-and-a-warm-up

Step 7. An agent that does not know which tool does a refactoring asks the `guide` tool, as it would run
`gabier help --for="<task>"`, and learns whether jdtls does it and with which tool.

Done when:
- `tools/list` has a tool `guide` with a required string parameter `task` (the task, in English), read-only, whose
  description says when to call it; `tools/call` answers with `AskTheGuide` and the guide of the cli, in the same words
  as `gabier help --for` for the same task, with the command names as tool names where the text says a command;
- a task the guide does not know is answered as `help --for` does (nothing matches), not as an error; a missing or blank
  `task` is `isError: true` as an invalid value;
- a test per case above (a known task, an unknown task, a blank task), and one that checks that every tool the guide
  names exists in `tools/list`;
- `docs/commands/mcp.md` and `mcp-instructions.md` name the tool; the skill `extend-guide` says that a guide entry
  now serves both the command line and MCP;
- `bin/ci` is green.
