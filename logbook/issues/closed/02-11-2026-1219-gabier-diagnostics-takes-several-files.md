# gabier diagnostics takes several files

Opened: 02-11-2026 12:19

Friction met by a worker (batch of the jdtls workspace tickets): `gabier diagnostics <file1> <file2>` answers a usage error, though the adapter asks
jdtls for several files with one wait for it to be quiet (`JdtlsLanguageServer.diagnostics(List)`: the cost of a request is that wait, so a batch
costs one). An agent that edited three files pays three waits of 5 s, or checks only one.

Done when:
- [ ] `gabier diagnostics <file>...` answers the diagnostics of each file in the order given, text and `--json`, one wait for jdtls to be quiet
      (a scenario or an adapter test shows one wait for several files); a file outside the project is refused for that file only or for the
      call, said in the doc, tested.
- [ ] `docs/commands/diagnostics.md` and `help` say it. `bin/ci` passes.

## Resolution

Commit 145a636 (pull request 42, merged fast-forward). `bin/ci` of the worker, full run on the last commit: format passed, build passed, test passed (cli 1554, core 727, daemon 1022, protocol 414, shared 236, architecture 22; coverage 100%), release-install and release-notes skipped (no changed file reaches them), native passed, jdtls passed; coupling ceilings unchanged (159 scenarios). Check of pull request 42 (`bin-ci`): pass.

- `gabier diagnostics <file>...` asks jdtls for all the files and waits once; the answer keeps the order given, a file given twice is answered twice and opened once; one file outside the project refuses the whole call before any file is opened (`DiagnosticsMethodTest`, `JdtlsLanguageServerTest`, replay in `InstalledDaemonTest`).
- Breaking change on purpose: the daemon method `diagnostics` takes `files` and always answers `{"files":[...]}`, so the `--json` of one file and the MCP tool `diagnostics` (a `files` array) changed shape. `docs/commands/diagnostics.md`, `docs/protocol.md`, `docs/commands/mcp.md`, the README and the guide say it.
- Review: no blocking finding. The one suggestion (a Javadoc that sits on `withNullMarked()` instead of `withProblems()` in `Scenarios.java`) is a typo fix made by the supervisor.

Tokens:
- worker: 315858 (one worker for the batch of three issues, one commit each)
- reviewer: 145126 (one review of the batch)
- supervisor: 30 calls, output 13576, input + cache write 164895 (cache reads 2684070), over the period of the batch (launch of the worker 10-11-2026 23:46 UTC to the merge), other work included; exchanges with the user: 4 messages
