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
