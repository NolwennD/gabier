# The failure log and the refusal `language-server-request-failed`: review suggestions

Opened: 11-11-2026 04:15
On hold: findings of the review of the batch `ca87719..dd80304` (pull request 44), none breaks a `Done when` point and no use met yet (`.claude/rules/workflow.md`, "Value first"); the user, or the supervisor when a use makes one matter, lifts the hold.

Done when:
- [ ] `RequestHandlerTest.leavesTheParametersOutOfTheLog` and the `DaemonServerTest` check of `Broken.java` can fail: a request whose exception message holds the path of its file shows what the log does with it (`EditingException.readFailed/writeFailed/rollbackIncomplete` carry a path); the log, or `docs/daemon.md`, says what it holds.
- [ ] A failure is written to the log as one block (`Main.java:150`, `RequestHandler.java:88`), so two clients failing at once do not interleave the lines of a "caused by" chain.
- [ ] The reason `FakeOnlyReasons.JDTLS_FAILS` and the `FakeOnly` labels agree on a scripted -32603: the nine tests that still carry the label for it, and the new rename test that does not, are treated alike.
- [ ] The Javadoc of `FailedRenameScenarios` gives the cause of the module scenario (a null element), not that of the record component; the claim that a later segment of a module name is refused as `language-server-no-such-symbol` (`gabier help rename`, `Operations.RENAME`) has a scenario, or is dropped.
