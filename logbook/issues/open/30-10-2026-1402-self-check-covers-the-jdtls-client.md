# The self-check covers the jdtls client on the module path

Opened: 30-10-2026 14:02

`module-info.java` of the daemon has `opens fr.craft.gabier.daemon to com.google.gson, org.eclipse.lsp4j.jsonrpc`:
without it, LSP4J throws `InaccessibleObjectException` on `JdtlsClient.status` on the module path. Nothing in `check`
notices: dropping the line passes the build and breaks the installed daemon.

Done when:
- [ ] `--self-check` (task `:daemon:selfCheck`) exercises what needs the `opens`: the LSP4J endpoint of `JdtlsClient`
      (`ServiceEndpoints.getSupportedMethods`) and Gson on `JdtlsClient.Status` and `PublishDiagnosticsParams`.
- [ ] Removing the `opens` line makes `:daemon:selfCheck` fail (shown once, then the line is restored).
- [ ] `bin/ci` green.
