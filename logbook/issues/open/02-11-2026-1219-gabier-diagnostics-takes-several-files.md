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
