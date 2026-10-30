# A test fails when the daemon is exempted again from the contexts rule

Opened: 30-10-2026 19:12

`HexagonalArchTest.shouldNotDependOnOtherBoundedContextDomains` (lines 147-149) is the only test that runs the rule over
the real classes, and nothing fails if its loop skips `daemon` again: the two tests added by
`daemon-exemption-in-the-architecture-rule-is-narrow` call `notDependOnOtherDomains(DAEMON_PACKAGE, ...)` directly.
With `contexts.stream().filter(c -> !c.equals(DAEMON_PACKAGE))` restored in that loop, every test stays green and the
daemon is exempt again, which is the regression that ticket targeted.

Done when:
- a test fails when the loop over the contexts skips `daemon` (shown once with the filter restored, then removed): the
  test uses the same list of contexts and the same helper as the loop, or asserts that the list holds `daemon`;
- `bin/ci` green.
