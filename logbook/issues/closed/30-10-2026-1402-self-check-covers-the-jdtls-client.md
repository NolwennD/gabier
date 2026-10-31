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

## Resolution

Merged as `f964df3` (last commit of the batch).

Checked by the worker's final `bin/ci --all` on the branch of the batch (log kept by the worker): format 5s, build 2s, test 74s (cli 71, core 259, daemon 366, protocol 320, shared 236, architecture 19, 0 failures), native 7s (cached build), jdtls 184s (real jdtls). `--self-check` now checks that `JdtlsClient` declares `language/status` and `language/eventNotification` as supported methods, that a notification sent through `ServiceEndpoints.toEndpoint(JdtlsClient)` reaches the client, and that Gson reads `JdtlsClient.Status` and `PublishDiagnosticsParams`; the seam is the record `SelfCheck.Machinery`, nine tests in `SelfCheckTest$OnTheJdtlsClient` cover each failure branch. Mutation shown once: removing the `opens` of `module-info.java` made `:daemon:selfCheck` fail (`InaccessibleObjectException`, `JsonIOException`).

Review (one pass): nothing reported on this issue.

Tokens:
- worker: 804170 for the whole batch (notifications 345758 and 458412, the second run after the review; the second figure may already include the first), shared by the five issues
- reviewer: 106756 (the first reviewer was stopped on the user's order and gave no figure; its work was redone), shared by the five issues
- supervisor: shared across the lifecycle batch, see the first issue
