# gabier mcp

`gabier mcp` is the MCP server of gabier, for an agent host that speaks the [Model Context Protocol](https://modelcontextprotocol.io)
(`claude mcp add gabier -- gabier mcp`). It reads one JSON-RPC 2.0 message per line on the standard input, writes one per
line on the standard output, and ends when the input is closed. The standard output carries the messages of the protocol
**and nothing else**: the notice of a daemon that is starting, like every log, goes to the standard error. It takes no
argument and no option (`--json` changes nothing).

Every operation of gabier but `start` and `stop` is a **tool**, named as the operation (`rename`, `diagnostics`,
`move`...) and generated from its descriptor in `eu.gabier.cli.domain.Operations`: its description is the summary and the
notes of the operation, its input schema is its parameters (a text, a whole number, a position, `true` or `false`, a list of
texts), all required. A **position is an integer counted from 1**, as on the command line; a list is a JSON array
(`files` of `move`, `cleanUps` of `cleanup`). A parameter added to an operation shows in its tool with no other file
edited. A path (`file`, `files`) may be relative: it is made absolute from the folder `gabier mcp` runs in, as the command
line does. `diagnostics`, `references`, `actions` and `status` are annotated `readOnlyHint`. The daemon is the one of the
project (`GABIER_PROJECT`, else the nearest folder with a `.git`), started by the first call that needs it, as any command
does; `status` asks a daemon that runs and starts none. Starting the server asks no daemon.

## The two eras of the protocol

One server answers both:

- **Revision 2026-07-28**: no handshake. Each request carries `_meta` with `io.modelcontextprotocol/protocolVersion` and
  `io.modelcontextprotocol/clientCapabilities`; `server/discover` says what the server is; every result has `"resultType":"complete"`
  and the server's `io.modelcontextprotocol/serverInfo` in its `_meta`; `server/discover` and `tools/list` say how long they
  may be kept (`ttlMs`, `cacheScope`). A version in `_meta` that the server does not serve is the error `-32022`, with
  `data.supported` and `data.requested`.
- **Revision 2025-11-25 and before**: the client opens with `initialize` (answered with its version when it is 2024-11-05,
  2025-03-26, 2025-06-18 or 2025-11-25, else with 2025-11-25) and `notifications/initialized`; its requests carry no `_meta`
  and the results have no `resultType`. A request with no `_meta` before `initialize` is `-32602`.

`ping` is answered `{}` in both. A method the server does not know is `-32601`; a line that is not JSON is `-32700` with a
null id; a message that is no request is `-32600`.

## `tools/call`

`{"name":"rename","arguments":{"file":"src/main/java/demo/Greeter.java","line":6,"column":19,"newName":"welcome","dryRun":true}}`
runs the operation with the one entry point of the application (`OperationRunner.run`), as the command line does, and the
result is:

- `content`: one text block, **the text the command line prints** for the same case (the `dry run, nothing written` line, the
  warnings, the files and the diff; `file:line:column: severity: message` for `diagnostics`, with the file as given; the
  places of `references`; the numbered `actions`; the line of `status`), so an agent reads the same words in both. An
  operation that changes files says `nothing to change` when the daemon changed nothing (`organize-imports` and `cleanup` do
  the same on the command line). The one difference is a file with no problem: `diagnostics` says `no problems`, where the
  command line prints nothing, because a text block with nothing in it is rejected by many hosts and by the Claude API;
- `structuredContent`: the result of the daemon as it gave it, which `--json` prints (the positions counted from 0, as the
  protocol counts them);
- `isError`: `false`.

A call that does not work is still a result, with `isError: true`, so that the agent reads the reason and tries again:
`content` is the problem as the command line shows it on the standard error (the line of text, then the pointer `Usage and
other ways: gabier help <command>` for a refusal where the language server declined the operation), and `structuredContent`
is the problem object of `--json` (`type`, `title`, `detail`, `key`, `parameters`, and `help` when there is a pointer). An
unreachable or failing daemon is such a result (`daemon-start-failed`...), and so is a failure gabier does not know
(`internal-server-error`, without its message, which goes to the standard error with its cause). The server goes on to the next message. A call that is no call is an error of the
protocol, `-32602`: a tool that does not exist (`start` and `stop` are not tools), a `name` that is not a text, `arguments`
that are not an object.

## Refusals

`gabier mcp` itself ends only with this one; everything else a call meets is a result of that call, below.

- `usage-error`: the arguments are not those of the command (gabier help <command> gives its usage).

## Failed calls

A call that does not work is a result with `isError: true`, not an end of the server. Its problem has the key of the
refusal of the operation that the call runs (the page of its command lists them, `gabier help <command>`) or one of these:

- `operation-invalid-values`: a value is missing, is not a parameter of the operation, or is not of the kind of its
  parameter (a position below 1, a list with an element that is empty or holds a line break...).
- `daemon-start-failed`: no daemon answered and `gabier-daemon` could not be started.
- `daemon-connection-failed`: the connection to the daemon was lost, or it hung up without answering.
- `daemon-not-running`: a call of `status` when no daemon answers; `status` never starts one, every other tool does.
- `internal-server-error`: a failure gabier does not know; the problem says nothing of it, and the server logs it on the
  standard error with its cause.
