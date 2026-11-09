# `gabier mcp` carries instructions, a warm-up and its documentation

Opened: 09-11-2026 12:08
Depends on: an-agent-runs-a-gabier-operation-through-mcp

Step 6, third part. A host that connects to `gabier mcp` gets the guidance of an agent on how to use gabier, jdtls is
started before the first call, and a person can find how to add the server to their host.

Done when:
- `instructions` (of `server/discover` and of the `initialize` result) come from a resource
  `mcp-instructions.md` next to `agents.md` (what an agent must know: use the tools instead of editing Java as text,
  positions from 1, `dryRun` first on a change across files, `diagnostics` after a change, the `guide` tool before
  editing by hand), declared in the native-image resource configuration, with a test like `AgentInstructionsTest`
  (the file exists, is not blank, names only tools that exist);
- the first `server/discover` or `initialize` starts the daemon in the background (`start`, as `gabier start` does), so
  that jdtls imports the project while the host loads the tools; a failure to start is logged on stderr and does not
  fail the handshake;
- `docs/commands/mcp.md` (the command, the tools, the two eras, the transport, errors) and a line of the README that
  gives the command to add the server to a host (`claude mcp add gabier -- gabier mcp`, and the `.mcp.json` form;
  verify the syntax in the documentation of Claude Code); `docs/protocol.md`, `docs/cli.md` and the skills
  `add-daemon-method` (a new operation is a new tool, by itself) and `verify` are brought up to date;
- verified by hand on the installed build, reported in the resolution: `gabier mcp` fed a `server/discover`, a
  `tools/list` and a `tools/call` of `diagnostics` on a broken file of this repository by `printf`;
- `bin/ci` is green.
