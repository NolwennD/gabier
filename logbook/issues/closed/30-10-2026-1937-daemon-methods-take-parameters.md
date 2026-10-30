# The methods of the daemon take parameters

Opened: 30-10-2026 19:37

A request of the daemon carries no parameters yet (`RpcRequest`, `DaemonMethod.call()`; `.claude/skills/add-daemon-method`,
step 2). The first operation of the catalogue (`diagnostics`, which needs a file) cannot be written without them.
JSON-RPC 2.0 gives the shape: a `params` object next to `method`, read by the daemon, written by the client.

Done when:
- a request may carry a `params` object of string members, read by `RpcRequest` and handed to the method; a request
  without `params` still works, as `status` and `stop` do;
- a method that gets a parameter it does not know, or lacks one it needs, refuses with a problem object of its own
  (`badRequest`, the name of the parameter in `parameters`), shown by a test with a scripted method on `RequestHandler`;
- a `params` that is not an object, or whose member is not a string, is refused the same way, and the daemon goes on;
- the client writes `params` (`DaemonClient`), shown against `ScriptedDaemon`;
- the skill `add-daemon-method` says how a method reads its parameters, and is applied once to the scripted method;
- `bin/ci` green, coverage 100%.

## Resolution

Merged as `169db64`.

Checked by the worker's `bin/ci` (full, one run): format 5s, build 9s, test 83s, native 124s, jdtls 93s. `RpcRequest` is
now `(id, method, params)`; the parameters are strings, stored sorted and unmodifiable, written sorted and omitted when
there are none; `DaemonMethod.parameters()` declares the names a method needs (all required, none optional yet) and
`call(Map<String, String>)` reads them; `RequestHandler` checks them before the call, so a refused request never
reaches the method. `DaemonClient.call(method, params)` writes them (used by tests only until `diagnostics`). The skill
`add-daemon-method` says how a method declares and reads its parameters and was applied once to the scripted method
`Reading` of `RequestHandlerTest`; the README paragraph on the protocol was updated. Tests: `JsonRpcTest.Requests`,
`RequestHandlerTest.Parameters` (an unknown, a missing, a not-an-object `params`, a not-a-string member, the daemon
answering after each), `DaemonClientTest.Call` (over streams and on a real socket against `ScriptedDaemon`).

Choice taken, challenged in the review: a malformed `params` is `-32600` with the key `invalid-request`, an unknown or
missing parameter `-32000` with `unknown-parameter` / `missing-parameter`, not JSON-RPC's `-32602`. It holds for our own
client (it reads `data.key`), not for a generic one: moved to the issue `parameter-errors-follow-json-rpc`.

Review (one pass): no BLOCKING finding. Suggestions, all in the issue `parameter-errors-follow-json-rpc`: `-32602`; a
repeated key in `params` keeps the last value without a test; hostile names untested (control character, very large
name or number of members); the order unknown-before-missing is not pinned; one test compares only `contains("\"result\"")`.

Tokens:
- worker: 108730
- reviewer: 61161
- supervisor: 11724 output, 25570 input + cache write over 10 calls (cache read 2912349), supervisor session over the
  period of the ticket (from the launch of the worker; the other work of the session included); exchanges with the user:
  0 messages
