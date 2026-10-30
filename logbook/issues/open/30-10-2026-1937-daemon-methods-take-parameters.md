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

Depends on: 30-10-2026-2001-strings-with-escapes-become-text-blocks (the same tests and sources: the text blocks pass
comes first, then the diagnostics chain).
