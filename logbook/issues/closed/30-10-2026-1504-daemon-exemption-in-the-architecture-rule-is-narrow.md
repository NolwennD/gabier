# The daemon's exemption in the architecture rule is narrow

Opened: 30-10-2026 15:04

`HexagonalArchTest.shouldNotDependOnOtherBoundedContextDomains` skips the whole `daemon` context, so a class of the
daemon could depend on the domain of any context. The adapter only needs `fr.craft.gabier.editing.domain..`
(it implements the port of `core`). Found by the review of the diagnostics adapter.

Done when:
- [ ] The rule still applies to `daemon`, except for the `editing` domain it implements; its Javadoc says so.
- [ ] A test shows the rule fails when a `daemon` class depends on another context's domain (the `protocol` domain, for
      instance), then the temporary class is removed.
- [ ] `bin/ci` green.

## Resolution

Merged as `0fbf008`.

Checked by the worker's `bin/ci` (full, on the worktree): format passed (after `hk fix --all`), build passed, test
passed (the `:architecture` tests, 18), native skipped ("no changed file reaches the native cli or the daemon"), jdtls
passed. With the exemption emptied the real rule and `shouldLetTheDaemonDependOnTheDomainItImplements` went red (2 of
18); restored, all 18 pass.

The rule is built by `notDependOnOtherDomains(context, allContexts)`; `daemon` is no longer skipped and the map
`IMPLEMENTED_DOMAINS` (`daemon -> editing`) is the one exemption. `quality.md` and the skill `verify` say so.
Deviation from the issue: the "temporary class, then removed" step became permanent test-only fixtures
(`daemon/violation/*`, `protocol/domain/ForeignDomainType`), since `protocol` has no domain package; the rules import
with `DO_NOT_INCLUDE_TESTS`, so the real rules never see them (confirmed by the review).

Review (one pass): no BLOCKING finding. Suggestions: the loop of `shouldNotDependOnOtherBoundedContextDomains` can skip
`daemon` again with every test green: issue `a-test-fails-when-the-daemon-is-exempted-again`; the positive test proves
`editing` on the real class `ChangeSet`, which a rename would break: left, minor.

Tokens:
- worker: 93410 (its last completion notification; it was resumed by messages)
- reviewer: 47389
- supervisor: 42945 output, 84933 input + cache write over 40 calls (cache read 4436137), supervisor session over the
  period of the ticket (from the launch of the worker; the three tickets run in parallel with it and the other work of
  the session included); exchanges with the user: 2 messages
