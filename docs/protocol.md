# Protocol

The cli and the daemon talk JSON-RPC 2.0 on a Unix domain socket, one JSON object per line (no framing header). A
request is `{"jsonrpc":"2.0","id":1,"method":"status"}`. A request may carry `params`, an object whose members are
strings (`"params":{"file":"src/A.java"}`): a method refuses, as a bad request, a parameter it does not take
(`unknown-parameter`) and one it needs and lacks (`missing-parameter`), the name of the parameter in the `parameters` of
the problem, and a value it cannot read (`invalid-parameter`). A request that carries both an unknown and a missing
parameter is refused for the unknown one, the first by name when there are several; with only missing ones, the first
by name is named. A `params` that is not an object (an array, a string, a number, `null`) and a member of `params` that
is not a string are refused with the key `invalid-request` and the name of the offender in `parameter` (`params` or the
member). All of these are answered with the code `-32602` ("Invalid params"), the one a problem object that names a
`parameter` gets. A request without an `id` (a notification) and a batch are refused as invalid requests. An error is a
JSON-RPC error whose `message` is the title of the problem and whose `data` is the problem object: its `code` tells who
is at fault (`-32603` a failure of gabier, `-32602` a parameter refused, `-32700` a line that is not JSON, `-32600` not a
request, `-32601` unknown method, any other code, `-32000`, a refusal of the request).

**A key that is repeated in an object is refused**, at any depth of the request (`{"file":1,"file":"A.java"}`,
`{"id":1,"id":2,...}`, the same name written with and without a `\u` escape): the line is not read as a request, it is
answered with `-32700` (`malformed-json`, at the position of the second occurrence, without quoting the name) and an
`id` of `null`. This is the choice that cannot be misread: a request whose first `file` the sender meant, and the last
one the daemon read, is never run (RFC 8259 leaves it open and I-JSON, RFC 7493, forbids it). The same parser reads
the answers of the daemon. A parameter name or value with control characters is written escaped in the answer, which
stays on one line. A malformed line, an unknown method or a client that disconnects in the middle of a request leaves
the daemon running.

A line is read up to 1 MiB (1,048,576 bytes, its line break not counted); a longer one is refused at once as a bad
request (`line-too-long`, the limit in the `limit` of the problem, `id` null), the rest of it is dropped, and the
connection goes on with the next line. This limit is the only bound on the size of a parameter name or value: a request
whose `params` hold a name or a value that does not fit in the line is refused for its length, before it is read, and
there is no second, smaller bound.

## Methods

All the parameters of a method are required; a value is a string (`dryRun` is `true` or `false`, the lines and columns
are numbers from 0, and a list, `files` or `cleanUps`, is its elements one to a line, none empty). A front end that types
the values, such as the descriptor `Operations` and `OperationRunner` of the cli, writes each as its kind says
(`Parameter.Type`) and counts a position from 1. The page of each command says what it does. `help` and `init-agent` have no row: they are answered by the cli alone and never reach the
daemon (help, init-agent). The MCP server of the cli, `gabier mcp`
([mcp](commands/mcp.md)), serves each method of the table as a tool of the same name, with the same parameters, except
`start` and `stop`: they are not tools (the server starts the daemon itself when the host connects, and an agent that
stopped it would lose the tools that need it), while `status` is one, and `watch`, `watch-all` and `watch-affected-tests`, which answer more than once
(below): a tool answers once. The parameters are those of the table, with the positions from 1 and a list as an array, so a
method added here is a tool by itself; the warm-up of the server sends the daemon a `start`, as `gabier start` does.
A daemon that stops by itself when nobody uses it (see [the daemon](daemon.md)) answers a request that reaches it after
it decided, whatever the method, with the refusal `daemon-stopping` (a server error, exit code 1) and runs nothing; the
`status` result has the member `idle` (`timeoutMillis`, `leftMillis`) while that rule is on.

| Method                   | Parameters                                                                    | Command                  |
| ------------------------ | ----------------------------------------------------------------------------- | ------------------------ |
| `status`                 | none                                                                          | status                   |
| `start`                  | none (starts jdtls in the background, answers the result of `status`)         | start                    |
| `stop`                   | none                                                                          | stop                     |
| `diagnostics`            | `files`                                                                       | diagnostics              |
| `diagnostics-all`        | none (the language server chooses the files)                                  | diagnostics              |
| `watch`                  | `files` (absolute paths, one per line); answers more than once                | diagnostics              |
| `watch-all`              | none (the language server chooses the files); answers more than once          | diagnostics              |
| `watch-affected-tests`   | none; answers more than once                                                  | affected-tests           |
| `rename`                 | `file`, `line`, `column`, `newName`, `dryRun`                                 | rename                   |
| `rename-token`           | `file`, `token`, `newName`, `dryRun`                                          | rename                   |
| `organize-imports`       | `file`, `dryRun`                                                              | organize-imports         |
| `cleanup`                | `file`, `cleanUps`, `dryRun`                                                  | cleanup                  |
| `extract-variable`       | `file`, `startLine`, `startColumn`, `endLine`, `endColumn`, `name`, `dryRun`  | extract-variable         |
| `extract-method`         | `file`, `startLine`, `startColumn`, `endLine`, `endColumn`, `name`, `dryRun`  | extract-method           |
| `extract-constant`       | `file`, `startLine`, `startColumn`, `endLine`, `endColumn`, `name`, `dryRun`  | extract-constant         |
| `extract-field`          | `file`, `startLine`, `startColumn`, `endLine`, `endColumn`, `name`, `dryRun`  | extract-field            |
| `move-type-to-file`      | `file`, `line`, `column`, `dryRun`                                            | move-type-to-file        |
| `move`                   | `files` (absolute paths, one per line), `package`, `dryRun`                   | [move](commands/move.md) |
| `inline`                 | `file`, `line`, `column`, `dryRun`                                            | inline                   |
| `create`                 | `file`, `kind`, `dryRun`                                                      | create                   |
| `references`             | `file`, `line`, `column`, `includeDeclaration`                                | references               |
| `actions`                | `file`, `startLine`, `startColumn`, `endLine`, `endColumn`                    | actions                  |
| `apply-action`           | `file`, `startLine`, `startColumn`, `endLine`, `endColumn`, `title`, `dryRun` | apply-action             |
| `affected-tests`         | `files` (absolute paths, one per line)                                        | affected-tests           |
| `affected-tests-changed` | none                                                                          | affected-tests           |

## A method that answers more than once

`watch` and `watch-all` follow the problems of Java files. Their request is answered as any other, with the problems as
they are now, in the shape of the result of `diagnostics`; the daemon then writes, on the **same connection** and with no
request, **notifications**: JSON-RPC messages with a `method` and `params` and **no `id`**, which the client does not
answer. The answer is always the first line, and the notifications follow it in the order of what they tell. There are
three of them for `watch` and `watch-all` (`watch-affected-tests` has two more, below):

- `problems`: the `params` are the result of `diagnostics` (`{"files":[...]}`), for the files whose list of problems is
  not the one the client has been told, in the order of the paths; a file that **became clean** is there with an empty
  `diagnostics`, so that the client sees an error go away. The daemon tells the files that changed after each read of the
  project, nothing when none did.
- `notice`: the `params` are `{"message":"..."}`, a text for the person who reads. The daemon sends one when the system
  cannot follow the files of the project (events lost, or no more watches to register the folders with) and looks at them
  every second from then on.
- `ended`: the `params` are the error object of a failed request (`code`, `message`, `data` the problem object): the reason
  the subscription ends, such as a language server that cannot be read any more. Nothing is written after it, the
  connection stays open.

`watch-affected-tests` follows the tests that the changes on disk affect. Its request is answered as `affected-tests-changed`
answers (`{"tests":[...]}`, for the changes now, which it uses up); the daemon then writes the notifications `affected-tests`
(the `params` are `{"tests":[...]}`, never with no test: a change that affects none is not told), `refusal` (the `params`
are the error object of a refusal of the testing context, such as a changed file that does not compile: **the subscription
goes on**, the changes are kept and their tests are told once they can be), `notice` and `ended`, as above. Unlike the
problems, which are a state the next one replaces, the tests of a change are something that happened: a client that reads
slowly is told the tests of the changes it missed together, once each, and none is lost. The daemon reads the tests once for
each change on disk whatever the number of watchers, only while a client follows them, with the thread that follows the disk
for the problems; the changes belong to the daemon, so what a watcher is told is no longer told to a call of
`affected-tests-changed`, and the other way round.

A client that sends a request while it follows is answered as usual (the answers and the notifications are written by one
thread, so two lines never mix); a second `watch` on the same connection is refused with `already-watching`. **The client
ends its subscription by closing the connection**: the daemon then stops reading the project for it, and for nobody when it
was the last one (no thread is left), and nothing else changes for the other clients. A client that does not read is told
the **latest state** when it reads again, not every state in between (the daemon keeps one state per client, so its memory
is bounded and no other client waits for it); a file that breaks and is fixed between two reads of a slow client is not shown to it.
A daemon that stops ends the connections of its watchers. A client that follows is a connected client: the daemon does not
stop by itself for being idle while one is there. An `RpcMessage` of `eu.gabier.protocol` reads a line of the daemon as a
response or a notification.
