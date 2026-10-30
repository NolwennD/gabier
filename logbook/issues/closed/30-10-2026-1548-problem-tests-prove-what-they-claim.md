# The tests of the problem objects prove what their names say

Opened: 30-10-2026 15:48

The review of the problem objects found four tests that prove less than they say:
- `Gabier.run` (`Gabier.java:24-36`): only `fail` is called directly in the tests. Changing `catch (RuntimeException e)`
  to `catch (UsageException e)` keeps every test green, yet an unexpected exception would escape with a stack trace.
- `ProblemMapperTest.aTechnicalErrorIsTheGenericInternalError`: the detail is the exception's message ("it broke"), not
  the generic text; the name says the opposite. The message is gabier's own by design: the test says so.
- `ProblemTest.keepsItsCategoryOutOfTheJson` asserts only `category()`; the JSON is proven by `isValidJson`.
- `bin/ci.d/40-native` checks the JSON by prefix and suffix only: `{"type":"urn:gabier:error:usage-error",}` passes.

Done when:
- [ ] A test of `run` with an unexpected `RuntimeException` from `execute` fails when the catch is narrowed to
      `UsageException` (shown once, then restored).
- [ ] The two tests are renamed or completed so that each name states what the assertions prove.
- [ ] The `native` stage asserts the whole JSON of the usage error, `"parameters":{"arguments":"--help"}` included.
- [ ] `bin/ci` green, the `native` stage included.
