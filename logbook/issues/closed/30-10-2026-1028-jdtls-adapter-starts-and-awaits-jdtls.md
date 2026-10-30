# gabier starts jdtls and knows when it is ready

Opened: 30-10-2026 10:28

The probe already starts jdtls, initializes it with the capabilities a refactoring needs, and waits for it to be ready
without trusting a `sleep` (`ServiceReady`, the imported projects, a dead process or a status `Error` failing at
once). That code lives in the `integrationTest` source set. Move it to the main code of the `daemon` module as the
first jdtls adapter: start jdtls on a project root with its own `-data` folder, and give back a session that is ready,
knows its projects, and stops the whole process tree and deletes its data on close. The probe then uses the adapter.
The adapter lives under `fr.craft.gabier.daemon` (the LSP4J rule); if the hexagon rules of the architecture tests
conflict with that, stop and report. The client capabilities to declare are those of the Python driver of the design
(`applyEdit`, `documentChanges`, `resourceOperations`, `codeActionLiteralSupport`, `resolveSupport`...).

Done when:
- the adapter is covered at 100% without jdtls (an in-process fake language server over piped streams, for the waits and
  the failures); a part left to the probe because it is too complicated to test is annotated with its reason, as the
  quality rule says;
- the probe starts jdtls through the adapter and no longer holds its own copy of the plumbing; it stays green on the
  real jdtls, and a killed jdtls still makes it fail in seconds;
- nothing is left behind after a run (process tree, data folder).

Depends on: core-operations-on-a-fake-language-server (the core comes first, with the fake; the adapter then makes it real).

The daemon will depend on the domain of the core (the change set, the port): the architecture rule that keeps the
contexts off each other's domain sees `daemon` as a context. The daemon is the composition root and the home of the
adapters, so the rule is adjusted here (it is part of this ticket) and the reason is said in the resolution.

## Resolution

Done in e9f484f.

- `bin/ci` (worker, one full run): format, build, test, jdtls passed; native skipped ("no changed file reaches the native cli"). Tests: cli 9, core 218, daemon 80, quickProbeTest 27, protocol 4, shared 236, architecture 16, 0 failures. JaCoCo 100% line and branch, no coverage exclusion left in the adapter.
- Kill proof: `kill -9` of jdtls by pid made the probe fail with `jdtls exited with code 137 ...`; no process or `/tmp/gabier-jdtls-data-*` left.
- Review (once): one blocking finding, fixed in the commit: the exclusion on `JdtlsSession.awaitEnd` was not justified (a hand-written `Process` reaches both branches: `StubbornProcess`). The `opens fr.craft.gabier.daemon to com.google.gson, org.eclipse.lsp4j.jsonrpc` in `module-info.java` is needed (LSP4J reflects on `JdtlsClient.status`, Gson fills `Status`) and was confirmed by the review.
- Left open as issues: orphaned jdtls child when the launcher dies before `close()`; fragile tests in `JdtlsSessionTest`; `selfCheck` blind to the jdtls client on the module path; package conflict reported by jdtls on the integrationTest sources.
