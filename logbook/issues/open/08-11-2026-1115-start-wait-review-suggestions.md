# The notice of the first wait and the state of status say only what they show (review suggestions)

Opened: 08-11-2026 11:15
On hold: findings of the review of the batch `the-first-call-says-why-it-is-slow`, `status-shows-the-indexing-of-jdtls`; none breaks a `Done when` point or a real use met so far.

- `modules/daemon/src/main/java/eu/gabier/daemon/StartProgress.java:42` and `gabier help status`: the doc says
  the numbers in front of the statuses of jdtls are not shown, but a launch status is kept whole, so `gabier status`
  prints `launching jdtls for 7s: 45% Starting Java Language Server` (`StartProgressTest` asserts it). Strip the prefix or
  correct the doc.
- `modules/cli/src/main/java/eu/gabier/cli/WaitNotice.java:38`: when the first poll at 3 s finds no daemon yet, or jdtls
  `not-started`, the next poll is 15 s later, so the first line comes at about 18 s, not 3 s as the README and
  `docs/daemon.md` say; no test covers it.
- `WaitingForJdtlsTest.java:35`: the first test claims the answer stays on stdout and asserts only that `out()` is empty;
  no test covers a failing command with the notice (`docs/daemon.md` says it comes before the problem object).
- `WaitNoticeTest.java:128`: `eu.gabier.protocol.JsonRpc` written fully qualified; the "says nothing" tests assert an
  empty stderr after a 60 to 100 ms sleep and pass if the watcher thread never ran.

Done when:
- [ ] each point above is either fixed with a test, or answered in this issue's resolution with the reason;
      `bin/ci` passes.
