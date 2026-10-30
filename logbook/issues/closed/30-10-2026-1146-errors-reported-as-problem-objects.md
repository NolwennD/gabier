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

## Resolution

Done in 7766ed9.

- `bin/ci` (worker, full run on the final commit): format, build, test, native and jdtls passed. Tests: cli 24, core 219, daemon 131, protocol 45, shared 236, architecture 16, 0 failures. JaCoCo 100%. The `native` stage now runs the binary alone: `--help` gives the usage error on stderr with exit 2 and an empty stdout; with `--json`, a one-line JSON object with exit 2; `--version` unchanged.
- Shape: the data model of RFC 9457 without its HTTP side: `type` (`urn:gabier:error:<key>`, stable and non-resolvable, since nothing serves documentation), `title` (derived from the key, no table of keys), `detail`, and the extensions `key` and `parameters`. Left out: `status` and `application/problem+json` (HTTP; on the CLI the exit code replaces `status`) and `instance` (no resource to point at). `Problem.category` (the `ErrorStatus`) is not written: it only gives the exit code (0 success, 1 internal error, 2 request refused; table in the README). The JSON is written by hand because the native image has no third-party library; a test-only strict Gson parser checks it. An unexpected exception gives the generic `internal-server-error`, with no message, cause or stack trace. `protocol` now `requires transitive shared`, `cli` `requires shared` (no longer `static`).
- Process: the first worker was stopped by the user before its report; a second worker found its uncommitted work complete, verified it and delivered it unchanged.
- Review (once): no blocking finding. Suggestions, opened as issues: `run` is not shown to report an unexpected exception (only `fail` is tested); control characters of a parameter reach the terminal raw in text mode; test names and the `40-native` JSON check say more than they prove.

Tokens:
- first worker (stopped by the user): not available
- second worker: 67,214
- reviewer: 69,643
- supervisor: 34 calls, 25,604 output, 31,200 input + cache write, 5,543,068 cache read; exchanges with the user: 5 messages (a floor). The period runs from the launch of the first worker (15:04) to the closing, and includes talk with the user (unused code, the stop and the resume).
