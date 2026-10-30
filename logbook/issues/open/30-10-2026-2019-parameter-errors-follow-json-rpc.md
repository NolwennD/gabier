# Invalid parameters are refused the way JSON-RPC says, and hostile ones are tested

Opened: 30-10-2026 20:19

`daemon-methods-take-parameters` answers a `params` that is not an object with `-32600` (`invalid-request`), and an
unknown or missing parameter with `-32000`. JSON-RPC 2.0 reserves `-32600` for a request that is not a valid Request
object, and defines `-32602` "Invalid params" for these cases: a standard client that branches on the code sees
"invalid request" or a generic server error. Our own client reads `data.key`, so nothing breaks today. Found in the
review of that ticket.

Done when:
- `params` that is not an object (an array, a string, a number, null), a member that is not a string, an unknown
  parameter and a missing one are all answered with `-32602`, the problem object keeping its key and its `parameter`;
  the README says so; each case has a test on `RequestHandler`;
- a repeated key in `params` (`{"file":1,"file":"A.java"}`) is refused, or "the last wins" is stated and pinned by a
  test; the same for a repeated key elsewhere in a request, whichever is chosen is said in the README;
- a parameter name with a control character (`"a\u0001b"`) is answered on one line, the control character escaped; a
  name or a value over the line limit is refused and the daemon goes on, shown by a test;
- the order of refusals is fixed by a test (an unknown parameter and a missing one in the same request: which one is
  named);
- `aMethodThatTakesNoneIsCalledWhenTheParamsAreEmpty` compares the whole answer line, as its neighbours do;
- `bin/ci` green, coverage 100%.

Depends on: 30-10-2026-1937-the-daemon-answers-diagnostics-of-a-file (same files: `RpcRequest`, `RequestHandler`, the
tests of the daemon).
