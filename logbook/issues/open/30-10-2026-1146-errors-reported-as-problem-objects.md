# Errors reach the user as problem objects with a stable exit code

Opened: 30-10-2026 11:46

Every refusal of gabier (invalid position, project still importing, operation not supported...) is an exception of the
shared error package, with an `ErrorKey`, an `ErrorStatus` and parameters. One mapper, at the edge, turns any
exception into a problem object, and the CLI prints it. The shape follows RFC 9457 (problem details), whose data model is
independent of the transport, without claiming its HTTP side: `type` (a non-resolvable URI built from the key, for
instance `urn:gabier:error:<key>`), `title`, `detail`, the extensions `key` and `parameters`, no `status` and no
`application/problem+json`. The same object will travel later in the `data` of a JSON-RPC error or in an MCP tool
result, so it lives in the `protocol` module, shared by the CLI and the daemon, pure JDK and `shared` (the CLI native
image has no third-party library, so the JSON is written by hand). The first user is what exists: the usage error of
the CLI (unknown command, no argument), today a usage text and status 2.

Done when:
- a `Problem` record and a mapper from an exception exist in `protocol`: a `GeneratorException` gives its key, its
  parameters and its message, any other exception gives a generic internal-error problem that does not leak a stack
  trace; the detail and the title are English text, no message bundle;
- the problem is written as JSON by hand (correct escaping, stable member order), shown valid by parsing it with a real
  JSON parser in the tests, and as one readable line of text for a human;
- the exit code is derived from the problem by a small table, stable and documented (README): success, usage or request
  refused, internal error, each with its own code (the existing status 2 for a usage error is kept);
- the CLI reports its usage error through the mapper: text on stderr by default, the JSON object with `--json`, same
  exit code, the native binary shown to do so;
- the choice of the shape (RFC 9457 inspired, no `status`) and of the `type` URI is explained in the resolution, with
  what was left out of the RFC and why.
