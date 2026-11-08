# The category of a refusal crosses the port: one test through the round trip (review suggestions)

Opened: 09-11-2026 00:34
On hold: finding of the review of `the-cli-reaches-the-daemon-through-a-port`; no `Done when` point is broken and no use shows a wrong exit code; the user, or the supervisor when a use makes it matter, lifts the hold.

- `DaemonAnswersTest.java:59,71`, `SocketDaemon.java:131` (`refusal`) and `Gabier.java` (`problemOf`): the category of a `Problem` goes to
  `DaemonRefusal` and back through two hand-written copies of six fields. `category` is neither in `toText()` nor in `toJson()`, so
  only the exit code shows it, and every refusal test uses `ErrorStatus.BAD_REQUEST` (exit 2): a copy that fixed or swapped the
  category stays green. Case: the daemon answers a `Problem` of category `INTERNAL_SERVER_ERROR`; the cli must exit with the code of
  that category, not 2. `INTERNAL_SERVER_ERROR` appears only in `DaemonClientTest` and `DaemonLauncherTest`, never through a refusal.

Done when:
- [ ] A test makes the fake daemon answer a refusal of category `INTERNAL_SERVER_ERROR` and states the exit code of the cli; it fails
      when `SocketDaemon.refusal` or `Gabier.problemOf` fixes the category (shown by changing one for the check, then putting it back).
- [ ] `bin/ci` passes.
