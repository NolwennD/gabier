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

## Resolution

Commit 456bab2 (PR #36). `gabier mcp`, a primary adapter in `eu.gabier.cli.infrastructure.primary.mcp` (`McpServer`, `Incoming`, `McpExchange`, `Tools`, `McpFailure`), speaks JSON-RPC lines on stdio with the `Json` of the `protocol` module: no SDK and no library, the cli being native and having none (this reverses the "Gson" of the design spec of 29-10-2026, which the cli cannot take).

- Modern era (2026-07-28): per-request `_meta`, `server/discover` with `supportedVersions`, `capabilities`, `io.modelcontextprotocol/serverInfo`, `resultType: "complete"`, `ttlMs` and `cacheScope` on `server/discover` and `tools/list`; `-32022` with `data.supported` and `data.requested`. Earlier era: `initialize` echoes 2024-11-05, 2025-03-26, 2025-06-18 or 2025-11-25, else answers 2025-11-25; a request with no `_meta` before `initialize` is `-32602`. `ping` answers `{}` in both eras; `-32601`, `-32700`, `-32600` as the issue says.
- `tools/list`: one tool per operation but `start` and `stop`, named as the operation (so `rename` and `rename-token`, and `status`), `inputSchema` generated from the descriptor, `readOnlyHint` on `status`, `diagnostics`, `references` and `actions`. Proof: `McpServerTest` (Discovering, Initializing, Listing, Refusing, ReadingTheLines), `ToolsTest.GeneratedFromTheDescriptors`, `HelpTest` for the row of `mcp`, and the native check.
- Shipping: the native `gabier` binary (decision of the plan; a separate module or binary stays open for step 8).
- `Mcp` is declared `standalone(...)` although its calls ask the daemon: taken by `mcp-tools-read-well-to-an-agent`.
- `bin/ci` of the worker on 0b6b607: format, build, test, native (it runs `gabier mcp` of the built binary on `tools/list`, both handshakes, a parse error, then two `tools/call` against the fake-served daemon; check added to `bin/ci.d/40-native`), jdtls passed; release-install skipped (no changed file reaches `install.sh`). Check `bin-ci` of PR #36 green (9m56s).
- Review (neutral, once): nothing blocking. Findings: the empty text block of `diagnostics` on a clean file, command-line words in the descriptions of five tools, an unexpected exception not logged, `help mcp` refusals not telling the truth for a command whose calls ask the daemon (all in the issue `mcp-tools-read-well-to-an-agent`, ready); no wait or bound on a call while `ping` is not read, ids `1.0`/`1e2` rewritten, `ping` without `resultType`, tests of robustness, `file:""` sent as the working directory (all in the on-hold issue `mcp-keeps-answering-while-a-call-waits-and-reads-every-id`).
- Process: the worker used `sed` three times against the dogfooding rule (once in issue 1, twice in tests) and did not observe red first for every test of issue 2 (server and tests written together).

Tokens:
- worker: 276,134 (its completion notification).
- reviewer: 121,746.
- supervisor: 14 calls, output 14,086, input + cache write 353,721 (cache reads 2,385,854), covering the supervisor session from the launch of the worker (11:20 UTC) to the merge, other work included (the pull request on the entry-point layer and the opening of the MCP issues); exchanges with the user: 0 messages.
