# The test of the refusal order does not depend on the JVM

Opened: 30-10-2026 22:41

In `RequestHandlerTest` (`ReadingTwo`, `Set.of("beta", "alpha")`) the iteration order of `Set.of` changes with each
JVM, so `ofSeveralMissingParametersTheFirstByNameIsNamed` goes red without the `.sorted()` of the handler only on some
runs. Found in the review of `parameter-errors-follow-json-rpc`.

Done when:
- the scripted method declares its parameters in an order that is not the alphabetical one and does not vary (a
  `LinkedHashSet` or a `TreeSet` with a comparator), and the test goes red every time when the sort is removed.
