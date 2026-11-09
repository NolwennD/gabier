# `gabier mcp` answers the handshake and lists the tools

Opened: 09-11-2026 12:08
Depends on: operation-descriptor-review-suggestions

Step 6, first part, of the plan `/home/nolwenn/.claude/plans/comment-int-gr-un-mcp-enchanted-parasol.md`. An agent whose
host speaks MCP can start `gabier mcp`, see that gabier is a server and read the typed list of what it does, which it
could not before. Nothing is run yet (next issue).

A new primary adapter `eu.gabier.cli.infrastructure.primary.mcp`, started by the command `gabier mcp`, on stdio: one
JSON-RPC 2.0 message per line on stdin and stdout (the stdio transport of MCP), JDK and the `Json` of the `protocol`
module only, no SDK and no library (the cli is native and has none). stdout is the channel: nothing else is written
there; logs go to stderr. Read the MCP specification, revisions 2026-07-28 and 2025-11-25, before writing.

Done when:
- the server answers both eras: `server/discover` (revision 2026-07-28: `supportedVersions`, `capabilities` with
  `tools`, `_meta.serverInfo` with the name and the version of gabier, `instructions` empty for now, results carry
  `resultType: "complete"`), and `initialize` then `notifications/initialized` (revision 2025-11-25 and earlier); `ping`
  is answered; an unknown method is `-32601`; a request naming an unsupported protocol version is `-32022` with the
  supported ones in `data.supported`; a malformed line is `-32700` and the server goes on;
- `tools/list` gives one tool per operation of `Operations` except `start` and `stop`, named as the operation, with its
  summary and notes as description, and an `inputSchema` (JSON Schema 2020-12, `additionalProperties: false`) generated
  from the parameters of the descriptor, whatever its type (`dryRun` a boolean, `files` and `cleanUps` an array of
  strings, positions integers counted from 1, said in the description); `diagnostics`, `references` and `actions`
  carry `annotations.readOnlyHint: true`;
- a test pins the generated list against the descriptors (a parameter added to an operation shows in its schema, with no
  other file edited) and one test per message above drives the server through its streams with no daemon;
- the shipping choice is the native `gabier` binary: `gabier mcp` is a command of the cli, with its page of `help` and
  its row in `Capabilities`; nothing about the transport is written in a daemon file;
- `bin/ci` is green, native stage included (the resources and reflection of the native image are checked by running
  `gabier mcp` of the build on a `tools/list`).
