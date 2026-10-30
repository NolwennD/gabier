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
