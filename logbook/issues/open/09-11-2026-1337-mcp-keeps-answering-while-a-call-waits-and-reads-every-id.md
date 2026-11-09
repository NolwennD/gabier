# `gabier mcp` keeps answering while a call waits and reads every id (review findings of PR 36)

Opened: 09-11-2026 13:37
On hold: review suggestions of PR 36 that break no `Done when` point; the first one matters for a host that pings, lift the hold if a host kills the server during a cold start.

Done when:
- a `tools/call` that waits for the daemon (start, jdtls import, a slow answer) does not stop the server from reading and answering `ping` meanwhile, and a call has a bound after which it ends as a failed call (`isError: true`, the problem `daemon-timeout` or the key the cli already has);
- a JSON-RPC `id` is echoed exactly as received: `1.0` and `1e2` are not rewritten, and an id the server cannot hold (above `Long.MAX_VALUE`) is answered, not dropped;
- `ping` carries `resultType: "complete"` in the modern era if the 2026-07-28 text says so (checked against the specification);
- tests for what only a regression would show: the `status` tool with no daemon (`isError`, `daemon-not-running`), a line of 1 MiB or more, EOF in the middle of a line, invalid UTF-8, an array nested deeper than 64;
- `gabier move ""` and a tool call with `"file":""` are refused as an invalid value, not sent as the working directory;
- `bin/ci` is green.
