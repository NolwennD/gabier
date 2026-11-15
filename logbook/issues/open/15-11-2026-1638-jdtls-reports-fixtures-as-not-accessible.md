# `gabier diagnostics --all` on gabier does not report as errors what Gradle compiles

Opened: 15-11-2026 16:38

On this repository `gabier diagnostics --all` reports about thirty files as errors that Gradle compiles: for example
`modules/daemon/src/test/java/eu/gabier/daemon/DiagnosticsMethodTest.java` ("The type `FakeJavaLanguageServer` is not
accessible"), `FakeServedMain`, the integrationTest sources, `RefusalKeysTest`, `CompiledSources`. Found by the worker of
`gabier test`. An agent that trusts the diagnostics here sees errors that are not there. The need: find where the difference
comes from and, per `.claude/rules/workflow.md` ("jdtls is configured, not fought"), a setting or a configuration of the import
that removes it, or the failure documented in the help of `diagnostics` and reported upstream.

Done when:
- The cause is stated in the resolution (the Gradle import of test fixtures and source sets under JPMS, or another), with the
  command that reproduces it.
- Either `gabier diagnostics --all` on this repository reports no error that Gradle does not, or the help of `diagnostics` says
  what the agent must know and the resolution names the upstream report.
