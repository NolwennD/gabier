---
name: add-daemon-method
description: Use to add a method to the daemon (a request the cli or another client can send on its Unix socket), such as an operation of the catalogue (diagnostics, rename) or a new administrative call. Also use to add the cli command that calls it.
argument-hint: '<method name and what it answers>'
---

# Add a method to the daemon

The daemon answers JSON-RPC 2.0, one JSON object per line, on its Unix socket. Its method table is the list given to
`RequestHandler` in the constructor of `DaemonServer` (`modules/daemon`). A method is one small class; nothing else in
the server changes. `status` (answers with a result) and `stop` (answers, then acts) are the two models.

## Steps

1. **Write the test first**, against `RequestHandler` and no socket (`RequestHandlerTest`, `DaemonMethodsTest`): the
   method's name, the `result` it answers, what it does once the answer is written, and its refusals. A refusal is an
   exception of the shared error package (`GeneratorException` with an `ErrorKey`, `badRequest` for a refused request,
   `internalServerError` for a failure of gabier): the handler turns it into the JSON-RPC error that holds the problem
   object, and any other exception into the generic internal error. Never catch it in the method. A method of the
   catalogue is run against the fake language server (`.claude/rules/workflow.md`, "Fake it until you run it"), never
   against a jdtls, in `check`: `DiagnosticsMethodTest` is the model (a `FakeJavaLanguageServer` scripted by the
   scenarios of the core, the request line through `RequestHandler`, the result read back with its `protocol` type).
2. **Write the method**: a package-private record implementing `DaemonMethod` (`name()`, `call()` returning an
   `Outcome` with the JSON of the result and, when needed, the `afterReply` to run once the answer is written, which is
   how `stop` answers before it stops). **Parameters**: the request carries them in `params`, an object of strings
   (`RpcRequest.params()`). The method declares the names it needs by overriding `parameters()` (none by default, all
   required) and reads them in `call(Map<String, String> params)`, which receives exactly those names: `RequestHandler`
   refuses before the call a request with a parameter the method does not take (`unknown-parameter`) or without one it
   needs (`missing-parameter`), as a `badRequest` whose `parameters` name it (the unknown one first, the first by name).
   The method never checks that they are
   there; it checks what they mean (a path that does not exist is its own refusal, like `fileMissing`; a value that
   cannot be read as what the method needs is `ParameterException.invalid`, key `invalid-parameter`). A value that is
   not a string, and a key repeated in `params`, are refused by `RpcRequest.parse`; a number or a list is written as a
   string and read by the method. Every refusal that names a `parameter` in its problem object is answered with the
   JSON-RPC code `-32602` (`JsonRpc.PARAMETER`): a refusal of your own that is about one parameter does the same, with
   `addParameter(JsonRpc.PARAMETER, name)`; one that is not keeps `-32000`.
   Test it with the scripted method of `RequestHandlerTest` (`Reading`): its parameters, what it read, and that a
   refused request never reaches `call`. The client writes them with `DaemonClient.call(method, params)` (in `eu.gabier.cli.infrastructure.secondary`).
   A method of the catalogue is a record around the use case of the core (`DiagnosticsMethod(Diagnose)`), which maps
   the domain types to the `protocol` ones; the positions stay 0-based in the protocol.
   A request has no optional parameter: an operation that can be asked in two ways is two methods (`rename`, by position,
   and `rename-token`, by identifier), which share the reading of their parameters (`ChangeRequest`, with
   `organize-imports`, which takes a file and `dryRun` only) and answer the same
   `protocol` type (`ChangedFiles`, which every operation that changes files will answer). A flag is a parameter whose
   value is `true` or `false`, anything else being an `invalid-parameter`; a method of the catalogue that writes files
   takes `dryRun`.
3. **Register it** in the `List.of(...)` of the `DaemonServer` constructor (which receives the `JavaLanguageServer` the
   use cases are built on: the `ServedProject` of the daemon, which starts jdtls at the first request that needs it). A
   test on a real socket (`DaemonServerTest`, a temporary directory, never the default path, the fake server) shows it
   answers there.
4. **A structured result is a type of `protocol`**, written and read with `Json` (`DaemonStatus` is the model: `toJson()`
   for the daemon, `of(Map)` for the client, which refuses a result that is not the expected one with an
   `InvalidResponseException`). The cli has no third-party library, so nothing else may parse it. A member that is not
   always there is an `Optional` of the record, written only when present and read as absent when missing
   (`DaemonStatus.jdtlsFailure`, the reason jdtls failed, is the model). A text that comes from jdtls (a reason, a
   message) is hostile input: the result carries it exactly, and whatever prints it for a person goes through
   `TerminalText.neutralise`, as the text of `gabier status` does.
5. **The cli command**, if there is one: a **capability** of `modules/cli`, a class of its own that holds its help text,
   in the package `eu.gabier.cli.infrastructure.primary` (the command line, with `Capabilities` and `CommandLine`).
   Five things, and no other file changes:
   - the **operation**, declared once in `eu.gabier.cli.domain.Operations` (no protocol type, no JSON): a constant
     `public static final Operation <NAME> = new Operation(method, summary, notes, refusals, parameters)`, added to
     `all()` in the order of the table of `docs/protocol.md`. It is the one place that names the **typed parameters**
     of the method (`Parameter.string`, `.integer`, `.bool`, `.array`: name as the daemon reads it, description; every
     parameter of a method is required; a position is counted from 1 on this side, and the description says so), the
     one-line **summary**, the optional **notes** (a warning to read before the call: `move-type-to-file` and `inline`
     are the models) and the **refusals** of the method, `<key>: <meaning>`, in the order `gabier help` lists them. A
     refusal that more than one operation answers is a constant of `Refusals` (the file, the language server and the
     stale edit ones), not a copy; a parameter that more than one shares is one of `Parameters`. An operation that can be
     asked in two ways is two operations (`RENAME`, `RENAME_TOKEN`). A key of the method that says the language server
     declined the operation (`language-server-not-...`) goes into `DECLINED_OPERATIONS` of `HelpPointer`, so that its
     refusal ends with the pointer to `gabier help <name>`. Any other front end (MCP) builds its tool from this
     descriptor, not from the command line;
   - a final class implementing `Capability` (`command()`, `run(Invocation)`), named after the command (`Rename`,
     `OrganizeImports`; `Diagnostics`, `Status` are the models). It declares what is the command line's own, once, as
     the constant `static final Command COMMAND = Command.called("<name>")`, which a step builder fills one part after
     the other, so that a part left out does not compile: `.usage(...)` (one line per form, each starting with
     `gabier [--json] <name>`), `.example(...)` (a call on a line that starts with `$ gabier `, and its answer), then
     the family of refusals and the operations it runs: `.asksTheDaemon(Operations.X...)` for a command that asks the
     daemon, starting it if need be (`usage-error`, `daemon-start-failed` and `daemon-connection-failed` are added to the
     refusals of its operations; a command with two operations, `rename`, has one usage line for each, in the order of
     its operations), `.asksARunningDaemon(Operations.X)` for one that never starts it (`status`, `stop`), or
     `.standalone(summary, refusals...)` for one that never talks to it (`help`, `init-agent`: they are no operation of
     the daemon, so they hold their own summary and refusals, and `.withNotes(...)` for their notes). The summary, the
     notes and the refusals that `gabier help` prints are those of the operations. The invariants (the usage names the
     command, the example starts with `$ gabier `) are `Assert`s of the compact constructor of `Command`. `command()`
     returns `COMMAND`. `gabier help`, `gabier help <command>` and `gabier <command> --help` (handled once, in the dispatch of
     `Gabier`, for every command) print it, and `UsageException` points to them: nothing else lists the commands, so the
     usage text is not edited. The refusals are the keys of the `## Refusals` section of the command's page (below):
     `CommandRefusalsTest` fails when they differ. `run` reads the words with `invocation.arguments()`
     (`Arguments`: `dryRun()`, `has(option)`, `operands(count, options...)` which takes `--dry-run` and the options out
     and refuses a wrong count, `exactly(count)` which keeps every word, `file(i)` as an absolute path, `position(i)` for
     `line:column` and `position(i, j)` for two numbers, both counted from 1 by the user and kept from 0; every refusal is
     the one usage error), asks the daemon and prints. A command of the catalogue (it asks for work on the project) calls
     `invocation.daemon().ask(method, params)`: `invocation.daemon()` is the port `Daemon` of `eu.gabier.cli.domain`
     (a method with string parameters in, the members of the result out as a `DaemonReply`, a refusal thrown as a
     `DaemonRefusal` that holds the problem as plain fields, no `protocol` type: the command decodes the reply with the
     `protocol` types, outside the domain). Its adapter, `SocketDaemon` in `eu.gabier.cli.infrastructure.secondary`
     (with `DaemonClient`, `DaemonLauncher`, `DaemonException`, `WaitNotice`), starts `gabier-daemon` when nothing
     answers (`DaemonLauncher`) and is built by `Gabier` (in `eu.gabier.cli`, which hands it to `CommandLine`), the one class outside the adapters that names it (a rule of
     `HexagonalArchTest`); a new way to the daemon is a method of the port, with its adapter. An
     administrative command that must not start the daemon (`status`, `stop`) calls
     `invocation.daemon().askRunning(method, awaitEnd)`.
     A command that answers with changed files shares `ChangedFilesReply` (`ask`, `print`, `isEmpty`: the list of files,
     the diff, the dry run), by composition, not by inheritance. A command that does not talk to the daemon (`help`)
     asks nothing and has no row in `docs/protocol.md`. Its output is text by default and the result with `--json`, a
     refusal of the daemon printed as its problem object with the exit code of its category (`DaemonRefusal`, turned into the `Problem` by `Gabier`);
   - the row of the method in the table of `docs/protocol.md`, with the same parameters: `OperationParametersTest`
     fails when the parameters of the operation, those of the usage line of its command (`<new-name>` for `newName`,
     `<line>:<column>` for `line` and `column`, two of them for the four of a range; a word it does not know fails the
     test, and is added to its table) and the row disagree, or when a row has no operation;
   - its name in the `permits` clause of the sealed interface `Capability`: a capability left out does not compile;
   - one `new` in the list of `Capabilities`, at its place in the order of `gabier help`. The list rejects two
     capabilities with one name when the class loads; no test compares it with `permits`.
   Test the rules of reading in `ArgumentsTest` when they are new (no socket), and the command in a test class of its own
   (`RenamingTest`, `InliningTest`), which extends `CliTest` and runs against `ScriptedDaemon` (a real socket, scripted
   answers; `daemonAnswering(result)`). That class holds only what is the command's own: the request it sends for a
   position, its special outputs. What every command shares is not rewritten: a command that returns changed files adds
   a `@Nested` class that implements `ChangesCommandContract` and gives its name, what follows the file on its command
   line and its request (`InliningTest.ReturningChangedFiles` is the model), and inherits the tests of the absolute path,
   `--dry-run`, the list and the diff, the terminal escapes and `--json`; add its command line to `DaemonAnswersTest`
   (refusal as text and as JSON, a result it cannot read) and the lines that are not its usage to `UnknownUsageTest`.
   Document it: a page `docs/commands/<command>.md` (modelled on the
   existing ones: usage, example in text and `--json`, a `## Refusals` section with one bullet `` - `key`: meaning `` per
   refusal, the daemon method and its parameters), one line in the
   "Capabilities" list of the README that links to it, and a row in the table of methods of `docs/protocol.md`. A file
   given on the command line is sent as an absolute path (the daemon's working directory is not the cli's), and a
   position is shown from 1 in the text and kept from 0 with `--json`.
6. **Prove it end to end**: add the command to `Daemon.check` in `bin/ci.d/40-native`, which runs the native `gabier`
   against the installed `gabier-daemon`, then run `bin/ci` (the `native` stage). A command of the catalogue is run there
   against the daemon served by the fake language server (`:daemon:installFakeServed`, main class `FakeServedMain` in
   the source set `fakeServed` of `modules/daemon`): it is a distribution of its own, never the one a user installs, and
   it is the only way to the fake (no option, no request). A new answer to script is a scenario of the core
   (`.claude/skills/add-scenario`) added to the list of `FakeServedMain`; the files of its project are written under the
   folder given as the argument of `gabier-daemon-fake`.
