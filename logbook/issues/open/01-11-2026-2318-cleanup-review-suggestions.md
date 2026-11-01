# gabier cleanup: review suggestions

Opened: 01-11-2026 23:18
On hold: findings of the review of 4bfce57, none breaks a `Done when` point; lifted by the user or when a use makes one matter.

Findings of the neutral review of `gabier cleanup`.

Done when:
- [ ] `CleanUpScenarios.java:97` (`clean-up-with-two-clean-ups`): the scenario shows the order, with two cleanups whose result differs by order, or the claim that it shows it is removed. `addFinalModifier` and `stringConcatToTextBlock` do not interact.
- [ ] `ScenariosTest.java:261` (`asksTheCleanUpsInTheOrderTheyAreAppliedAndKeepsTheCleanFileFreeOfConcatenations`): the test checks production code, or goes, since it only asserts the fixture's own request list.
- [ ] `Cleanup.java:38` and `docs/commands/cleanup.md:36`: a test ties the 19 ids of the help and of the doc to the `CleanUp` enum (renaming or adding a constant fails it).
- [ ] `JdtlsLanguageServer.java:1087`: when jdtls dies during `java/cleanup`, the caller sees the failure of the request, not the one of the reset in the `finally`; a test with a dead connection.
