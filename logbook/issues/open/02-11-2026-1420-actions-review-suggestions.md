# gabier actions and apply-action: review suggestions

Opened: 02-11-2026 14:20
On hold: findings of the review of bde4081, none breaks a `Done when` point; the user lifts the hold (the first and the fourth give a misleading answer, the third touches the coupling rule).

Done when:
- [ ] `ApplyCodeAction.java:64` and `JdtlsLanguageServer.java:937,964`: when the action offered at the listing has gone by the apply (the error fixed, the file edited), the refusal is `editing-no-such-code-action` with the titles on offer, not `language-server-not-applicable`; the apply path resolves the action it listed instead of listing again (which also removes the second 5 s quiet wait). `refusesAnActionJdtlsNoLongerOffers` (`JdtlsLanguageServerTest:3833`) pins the right key.
- [ ] Two actions of the same title: a test through `ApplyCodeAction`, the daemon and the native stage (the fake scripted with two actions titled "X", then `apply-action ... "X"`) shows the problem with its `choices` and nothing written.
- [ ] `CodeActionScenarios.java:148` and the `APPLY_THE_IMPORT_OF_A_TYPE` edit: the scenarios state the effect (the text of the file once the action is applied, compiling) and not the exact text-block indentation and import edit of jdtls, as the coupling rule of 02-11-2026 asks.
- [ ] `JdtlsLanguageServer.java:799,987`: an `InvalidRequest` of `textDocument/codeAction` is not turned into an empty list; `gabier actions` tells "jdtls declined the request" and not "no actions" when it declines for a range it cannot resolve.
- [ ] `JdtlsLanguageServer.java:937` (`codeActionEdit` uses only `getEdit()`): a test and the docs say that an action with an edit and a command (the "refresh diagnostics" jdtls adds to the create-class, create-method and create-variable fixes) is applied without the command.
