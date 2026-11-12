# gabier mcp

`gabier mcp` is the MCP server of gabier, for an agent host that speaks the [Model Context Protocol](https://modelcontextprotocol.io)
(`claude mcp add gabier -- gabier mcp`, see [Adding it to a host](#adding-it-to-a-host)). It reads one JSON-RPC 2.0
message per line on the standard input, writes one per
line on the standard output, and ends when the input is closed. The standard output carries the messages of the protocol
**and nothing else**: the notice of a daemon that is starting, like every log, goes to the standard error. It takes no
argument and no option (`--json` changes nothing).

## Adding it to a host

`gabier` must be on the `PATH` of the host (`mise use github:NolwennD/gabier@<version>`, or `mise run install` from a
clone). For Claude Code, from the project:

```sh
claude mcp add gabier -- gabier mcp
```

Everything after `--` is the command that starts the server, passed untouched. The server is then stored for you in
this project (`--scope local`, the default); `--scope project` writes it to the `.mcp.json` of the project, to share it
with the team (Claude Code asks each person to approve it), and `--scope user` makes it available in every project. The
same server in a `.mcp.json` at the root of the project (a server with no `type` is a stdio server):

```json
{
  "mcpServers": {
    "gabier": {
      "command": "gabier",
      "args": ["mcp"]
    }
  }
}
```

The handshake starts the daemon of the project the host starts the server in (`GABIER_PROJECT` to point elsewhere, as for
any command); a call acts on the project of its file ([The project of a call](#the-project-of-a-call)). `/mcp` in Claude Code shows the server, its tools and its instructions. (Syntax checked against the MCP page of the
documentation of Claude Code, <https://code.claude.com/docs/en/mcp>.)

## Instructions and warm-up

The handshake carries `instructions`, in `server/discover` (revision 2026-07-28) and in the result of `initialize` (revision
2025-11-25 and before): the resource `mcp-instructions.md` of the cli, what an agent must know to use the tools. Use them
instead of editing Java as text, count positions from 1, try a change that crosses files with `dryRun` set to true,
call `diagnostics` after a change, once with all the files that changed, and `organize-imports` after adding or removing a usage; `diagnostics-all` gives the problems of every Java file of the project. No tool follows the errors while the agent edits (a tool answers once, and `gabier diagnostics --watch` is a command that keeps running, not a tool): the instructions say to call `diagnostics` after each edit. A test keeps every tool the text
names in step with `tools/list`.

The first `server/discover` or `initialize` also **starts the daemon in the background**, as `gabier start` does (it answers
at once, jdtls starts in the daemon), so that jdtls imports the project while the host loads the tools. It delays no answer
and fails none: a daemon that cannot be started is told on the standard error (`gabier mcp: the daemon could not be
started: ...`), and the first call that needs the daemon meets the failure again and answers it as a failed call. A call for
that project made while the start is still running waits for it, so as not to launch a second daemon. A server that is closed before the daemon
is up does not wait for it.

The operations of gabier that work on the code or report on it are **tools**, named as the operation: `diagnostics`,
`diagnostics-all`, `rename`, `rename-token`, `organize-imports`, `cleanup`, `extract-variable`, `extract-method`, `extract-constant`,
`extract-field`, `move-type-to-file`, `move`, `inline`, `create`, `references`, `actions`, `apply-action`, `affected-tests`, `affected-tests-changed` and `status`.
`start` and `stop` are operations of the daemon but not tools: the server starts the daemon itself when the host
connects (below), and an agent that stopped it would lose the tools that need it. `watch` and `watch-all` are not tools either: they answer more than once, and a tool answers once. Each tool is generated from its descriptor in `eu.gabier.cli.domain.Operations`: its description is the summary and the
notes of the operation, its input schema is its parameters (a text, a whole number, a position, `true` or `false`, a list of
texts), all required. A **position is an integer counted from 1**, as on the command line; a list is a JSON array
(`files` of `move`, `cleanUps` of `cleanup`). A parameter added to an operation shows in its tool with no other file
edited. `diagnostics`, `diagnostics-all`, `references`, `actions`, `affected-tests` and `status` are annotated `readOnlyHint` (`affected-tests-changed` is not: it writes no file, but it uses up what the daemon remembers of the changes, so a host should not repeat it). Starting the server asks no
daemon; its first handshake starts one (below).

## The project of a call

A path (`file`, `files`) is **absolute**: a relative path is refused as an invalid value (`operation-invalid-values`, "must
be an absolute path"), because the server does not know the folder the session works in (a host may enter a worktree after
it started the server, and the server runs in the folder it was started in). This reverses the choice of the first calls of
the server, which made a relative path absolute from the folder of the server: that applied a call to the project of the
server, not to the one the agent was in. The command line is unchanged: it runs in a folder, so a relative path is
relative to it.

A call goes to the **daemon of the project of its file**: the nearest folder above the file that holds a `.git` (a folder
in a clone, a file in a worktree, so a worktree inside a clone is its own project), else the project the server was
started in (`GABIER_PROJECT`, else the nearest folder with a `.git` above its working directory). There is one daemon for
each project, reached the first time a call needs it and kept; the daemon of the project of the launch is the one the
handshake starts, and a call for another project starts its daemon itself, and waits for it as in [Calls that
wait](#calls-that-wait), the notices of progress telling where the jdtls of that project is. The files of one call must
be in one project: `move` with files of two projects is refused (`operation-invalid-values`, "must be in one project").
A call that names no file (`status`) is for the project of the launch. When `GABIER_SOCKET` replaces the socket of the
daemon, every project is that one socket, and a file outside the project of that daemon is refused by it
(`language-server-file-outside-project`).

## Calls that wait

A call that asks the daemon waits for it: for the start of the daemon, for the import of the project by jdtls (about a
minute for a Gradle project, on the first call of a session), or for a slow answer. The server **goes on reading
meanwhile**, so a host sees it answer:

- **Threads.** The server reads the client on one thread and answers on it what needs no daemon, in the order the messages
  come: the handshake, `ping`, `tools/list`, the tool `guide`, a call whose values are wrong (a value that is missing,
  unknown or of the wrong kind, an empty file) and a call that names no tool. A call that asks the daemon runs in a thread
  of its own, and its answer is written when it ends, which can be after the answer to a message that came later (the id
  tells them apart, as JSON-RPC means it to). **Calls are not ordered among themselves**: a host that needs one to end
  before the next waits for its answer. The server writes one message to a line, each whole, never mixed. At the end of
  the input the server waits for the calls that have not ended, so that what was asked is answered; it does not wait for
  the daemon that a handshake starts.
- **`status`** asks a daemon that runs and starts none, and does not wait for the start the handshake began: called on the
  side, it answers at once with the state of jdtls (starting, ready...) and, while jdtls starts, `jdtlsProgress` (launching
  or importing, the last task it reported, for how long). Called right after the handshake, while the daemon that the
  handshake started is not listening yet (the launch of the process, a few seconds), no daemon answers: the call then
  fails with the key `daemon-starting` ("the daemon is starting ... call status again in a moment") and not with
  `daemon-not-running`. The server decides that before it asks: the start of the handshake is still running. Once that
  start has ended (the daemon listens, or could not be started) a `status` that finds no daemon says `daemon-not-running`.
- **Progress.** A request that carries `_meta.progressToken` (a text or a whole number) is told how the call goes: every 5
  seconds, while the call waits, a `notifications/progress` with the token, a `progress` that grows by one at each notice
  and a `message` such as `jdtls is starting: importing the project for 15s: Build model (waiting 20s so far)` (or
  `waiting for the daemon (5s so far)` when no daemon says where jdtls is). No `total`: nobody knows it. The notices stop
  when the call ends, as the protocol requires (revisions 2025-11-25 and 2026-07-28 say the same of progress). A call that
  asked nothing is told nothing.
- **The bound.** A call that the daemon has not answered 11 minutes after it came ends as a failed call, `isError: true`,
  key `daemon-timeout`, and its thread is left to end by itself. The bound is above what the daemon allows itself (jdtls has
  5 minutes to start, then 5 for a request, and the cli 20 seconds to launch the daemon), so the daemon's own refusal, which
  says what went wrong, comes first; this one only catches a daemon that does not answer at all. The operation may still
  run in the daemon.
- **Cancellation.** A `notifications/cancelled` whose `requestId` is the id of a call that has not ended ends it with no
  answer and no more notices, as both revisions ask (the stdio page of 2026-07-28 makes it a MUST). The request of another
  id, one that has ended or a malformed notification is ignored. The server does not interrupt the thread of the call: the
  daemon may still run what was asked.
- **Ids.** The id of a request is given back as it was written: `1.0` and `1e2` stay as they are, and a whole number that
  no `long` holds is answered. A text or a whole number is an id, a fraction is not (`-32600`, null id). **No two answers carry the
  same id**: a request of any kind (a call, `ping`, `tools/list`, the `guide`, a call whose values are wrong...) whose id is
  that of a call that is still running, and so has not been answered, is refused with `-32600` and a **null id**, because the
  answer of the running call is the one that carries the id. The id can be used again once the call has been answered.
- **`ping`** is answered `{}`; a request that names the modern era in its `_meta` gets `{"resultType":"complete",...}`,
  which revision 2026-07-28 requires of every result (it removed `ping`, and hosts still send it).

## The `guide` tool

`guide` is the last tool and the one that is no operation of the daemon: it asks the guide of the improvements
([`docs/improve.md`](../improve.md), `gabier help guide`) as `gabier help --for="<task>"` does, and asks no daemon. An
agent that does not know which tool does a refactoring calls it before editing by hand, with `{"task":"turn this string
concatenation into a text block"}` (a required text, in English), and reads the one to three entries that fit, each with
its confidence and what to do (`Ask: cleanup ...`, or `By hand: ...` when jdtls offers nothing). It is annotated
`readOnlyHint`.

- **The words are those of `help --for`**: the same text (`GuideText`, `eu.gabier.cli.application`), the same ranking
  (`AskTheGuide`), so an entry of the guide serves the command line and MCP at once; the command line text does not
  change. Three differences, all for a tool (`ToolWords`): a command the text names as `gabier <command>` is written as
  the name of the tool of that name (`cleanup <file> stringConcatToTextBlock`; the words in angle brackets are its
  parameters); a form that is another tool is that tool (`gabier rename --token` is `rename-token`); an option of the
  command line is the boolean parameter of the same name set to true (`--dry-run` is `dryRun=true`,
  `--include-declaration` is `includeDeclaration=true`, which the description of the tool says), so the answer holds no
  `--option`; and the line that points to `gabier help guide` is left out.
- **A task that nothing matches** is an answer, not a failure: `Nothing in the guide matches "<task>".`, `isError` false.
- **A missing, blank or non-text `task`**, or another value, is a failed call with the key `operation-invalid-values` (the
  operation named `guide`).
- The result has a `content` text and no `structuredContent`.

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

`ping` is answered `{}` in both (see [Calls that wait](#calls-that-wait) for the modern era). A method the server does not know is `-32601`; a line that is not JSON is `-32700` with a
null id; a message that is no request is `-32600`.

## `tools/call`

`{"name":"rename","arguments":{"file":"src/main/java/demo/Greeter.java","line":6,"column":19,"newName":"welcome","dryRun":true}}`
runs the operation with the one entry point of the application (`OperationRunner.run`), as the command line does, and the
result is:

- `content`: one text block, **the text the command line prints** for the same case (the `dry run, nothing written` line, the
  warnings, the files and the diff; `file:line:column: severity: message` for `diagnostics`, with each file as given in `files`, and for `diagnostics-all`, with each file by the absolute path the daemon names it; the
  places of `references`; the numbered `actions`; the line of `status`), so an agent reads the same words in both. An
  operation that changes files says `nothing to change` when the daemon changed nothing (`organize-imports` and `cleanup` do
  the same on the command line). The one difference is a file with no problem: `diagnostics` and `diagnostics-all` say `no problems`, where the
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
  parameter (a position below 1, a path that is empty or not absolute, files that are not all in one project, a list with
  an element that is empty or holds a line break...).
- `daemon-start-failed`: no daemon answered and `gabier-daemon` could not be started.
- `daemon-connection-failed`: the connection to the daemon was lost, or it hung up without answering.
- `daemon-not-running`: a call of `status` when no daemon answers; `status` never starts one, every other tool does.
- `daemon-starting`: a call of `status` when no daemon answers yet and the start that the handshake began is still
  running: ask again in a moment.
- `daemon-timeout`: the daemon had not answered 11 minutes after the call came; the call is given up and the daemon may
  still be working on it (`status` tells).
- `internal-server-error`: a failure gabier does not know; the problem says nothing of it, and the server logs it on the
  standard error with its cause.
