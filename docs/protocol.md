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
are numbers from 0). The page of each command says what it does. `help` and `init-agent` have no row: they are answered by the cli alone and never reach the
daemon ([help](commands/help.md), [init-agent](commands/init-agent.md)).

| Method             | Parameters                                                                  | Command                                                    |
| ------------------ | --------------------------------------------------------------------------- | ---------------------------------------------------------- |
| `status`           | none                                                                        | [status](commands/status-and-stop.md)                      |
| `start`            | none (starts jdtls in the background, answers the result of `status`)      | [start](commands/start.md)                                 |
| `stop`             | none                                                                        | [stop](commands/status-and-stop.md)                        |
| `diagnostics`      | `file`                                                                      | [diagnostics](commands/diagnostics.md)                     |
| `rename`           | `file`, `line`, `column`, `newName`, `dryRun`                               | [rename](commands/rename.md)                               |
| `rename-token`     | `file`, `token`, `newName`, `dryRun`                                        | [rename](commands/rename.md)                               |
| `organize-imports` | `file`, `dryRun`                                                            | [organize-imports](commands/organize-imports.md)           |
| `cleanup`          | `file`, `cleanUps`, `dryRun`                                                | [cleanup](commands/cleanup.md)                             |
| `extract-variable` | `file`, `startLine`, `startColumn`, `endLine`, `endColumn`, `name`, `dryRun` | [extract-variable](commands/extract-variable.md)           |
| `extract-method`   | `file`, `startLine`, `startColumn`, `endLine`, `endColumn`, `name`, `dryRun` | [extract-method](commands/extract-method.md)               |
| `extract-constant` | `file`, `startLine`, `startColumn`, `endLine`, `endColumn`, `name`, `dryRun` | [extract-constant](commands/extract-constant.md)           |
| `extract-field`    | `file`, `startLine`, `startColumn`, `endLine`, `endColumn`, `name`, `dryRun` | [extract-field](commands/extract-field.md)                 |
| `move-type-to-file` | `file`, `line`, `column`, `dryRun`                                          | [move-type-to-file](commands/move-type-to-file.md)         |
| `move`             | `files` (absolute paths, one per line), `package`, `dryRun`                 | [move](commands/move.md)                                   |
| `inline`           | `file`, `line`, `column`, `dryRun`                                          | [inline](commands/inline.md)                               |
| `create`           | `file`, `kind`, `dryRun`                                                    | [create](commands/create.md)                               |
| `references`       | `file`, `line`, `column`, `includeDeclaration`                              | [references](commands/references.md)                       |
| `actions`          | `file`, `startLine`, `startColumn`, `endLine`, `endColumn`                  | [actions](commands/actions.md)                             |
| `apply-action`     | `file`, `startLine`, `startColumn`, `endLine`, `endColumn`, `title`, `dryRun` | [apply-action](commands/apply-action.md)                 |
