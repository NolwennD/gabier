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

## Resolution

Merged as `ad9d2b4` (rebased once over rules-only commits, so `bin/ci` was not run again).

Checked by the worker's `bin/ci` (full): format 6s, build 5s, test 187s, native skipped ("no changed file reaches the
native cli or the daemon"), jdtls 218s. The loop over the contexts is one helper in `HexagonalArchTest`,
`notDependOnOtherDomainsOfEveryContext(List<String>)`, which `shouldNotDependOnOtherBoundedContextDomains` runs on the
real classes; the new `BoundedContexts.shouldRunTheRuleOverTheDaemonAmongTheContexts` runs the same helper on the real
`CONTEXTS` list against the fixture `DependsOnAnotherContextDomain` and expects an `AssertionError` that names it.
Mutation shown once: `.filter(c -> !c.equals(DAEMON_PACKAGE))` added to the helper made that test fail (16 run, 1
failed) while `shouldNotDependOnOtherBoundedContextDomains` stayed green, which is the gap the issue described; then the
filter was removed.

Not reviewed: small ticket (a test of the architecture project, no production code), by the rule of `ship` step 1.

Tokens:
- worker: 57942
- reviewer: none (not reviewed)
- supervisor: 15819 output, 37384 input + cache write over 11 calls (cache read 3668730), supervisor session over the
  period of the ticket (from the launch of the worker; the other workers and the rest of the session are included);
  exchanges with the user: 0 messages
