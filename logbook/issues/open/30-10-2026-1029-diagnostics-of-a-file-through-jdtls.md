# The jdtls adapter gives the diagnostics of a file

Opened: 30-10-2026 10:29

The jdtls adapter implements the diagnostics of the port `JavaLanguageServer` (already defined and used by the core
with a fake): it opens the file, waits for the publication that follows its opening, then for jdtls to be quiet, and
answers in the domain types. This is the logic of the probe (`Diagnostics` and the settled wait), moved from the
`integrationTest` source set to the main code, the probe using it. It is the minimum every LSP server offers
(`publishDiagnostics`), and what the agents need after each edit.

Done when:
- on the real jdtls (the probe, with a temporary broken copy of a source of the checkout), the adapter answers an empty
  list for a clean file and the errors of a broken one, and fails with the typed refusal of the port for a file outside
  the project or a jdtls that is still importing;
- the unit tests of the adapter need no jdtls (an in-process fake language server over piped streams) and the coverage
  stays at 100%; what the core already proves with the fake of the port is not proved again here;
- no LSP4J outside `daemon`, the architecture tests green.

Depends on: jdtls-adapter-starts-and-awaits-jdtls (same adapter and same probe code).
