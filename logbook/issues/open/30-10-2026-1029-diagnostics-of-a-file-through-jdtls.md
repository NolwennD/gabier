# gabier gives the diagnostics of a file

Opened: 30-10-2026 10:29

First operation of the port `JavaLanguageServer` (domain of the core, no LSP type): the diagnostics of a source file,
as a list of severity, line, column and message. The jdtls adapter opens the file, waits for the publication that
follows its opening, then for jdtls to be quiet, and answers: the logic of the probe (`Diagnostics` and the settled
wait), moved from the `integrationTest` source set to the main code, the probe using it. This is the minimum every
LSP server offers (`publishDiagnostics`), and what the agents need after each edit.

Done when:
- the operation answers an empty list for a clean file and the errors of a broken one, on the real jdtls (a temporary
  broken copy of a source of the checkout, in the probe), and fails with a readable message for a file outside the
  project or a jdtls that is still importing;
- the unit tests need no jdtls (fake language server) and the coverage stays at 100%;
- the port, the domain type and the adapter respect the architecture tests, with no LSP4J outside `daemon`.

Depends on: jdtls-adapter-starts-and-awaits-jdtls (same adapter and same probe code).
