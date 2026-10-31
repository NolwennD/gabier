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
   refused request never reaches `call`. The client writes them with `DaemonClient.call(method, params)`.
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
5. **The cli command**, if there is one: in `Gabier.execute` (`modules/cli`), through `DaemonClient`. A command of the
   catalogue (it asks for work on the project) takes `starting(environment)`, which starts `gabier-daemon` when nothing
   answers (`DaemonLauncher`); an administrative command that must not start one (`status`, `stop`) takes
   `waiting(environment)`. Its output is text by default and the result with `--json`, a refusal of the daemon printed as its problem object with the exit code
   of its category (`DaemonRefusal`). Test it in `GabierTest` against `ScriptedDaemon` (a real socket, scripted answers).
   Document it: a page `docs/commands/<command>.md` (modelled on the existing ones: usage, example in text and `--json`,
   refusals, the daemon method and its parameters), one line in the "Capabilities" list of the README that links to it,
   and a row in the table of methods of `docs/protocol.md`. Then update the usage text of `UsageException`. A file given on the command
   line is sent as an absolute path (the daemon's working directory is not the cli's), and a position is shown from 1
   in the text and kept from 0 with `--json`.
6. **Prove it end to end**: add the command to `Daemon.check` in `bin/ci.d/40-native`, which runs the native `gabier`
   against the installed `gabier-daemon`, then run `bin/ci` (the `native` stage). A command of the catalogue is run there
   against the daemon served by the fake language server (`:daemon:installFakeServed`, main class `FakeServedMain` in
   the source set `fakeServed` of `modules/daemon`): it is a distribution of its own, never the one a user installs, and
   it is the only way to the fake (no option, no request). A new answer to script is a scenario of the core
   (`.claude/skills/add-scenario`) added to the list of `FakeServedMain`; the files of its project are written under the
   folder given as the argument of `gabier-daemon-fake`.
