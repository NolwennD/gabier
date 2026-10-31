# The test of the refusal order does not depend on the JVM

Opened: 30-10-2026 22:41

In `RequestHandlerTest` (`ReadingTwo`, `Set.of("beta", "alpha")`) the iteration order of `Set.of` changes with each
JVM, so `ofSeveralMissingParametersTheFirstByNameIsNamed` goes red without the `.sorted()` of the handler only on some
runs. Found in the review of `parameter-errors-follow-json-rpc`.

Done when:
- the scripted method declares its parameters in an order that is not the alphabetical one and does not vary (a
  `LinkedHashSet` or a `TreeSet` with a comparator), and the test goes red every time when the sort is removed.

## Resolution

Fixed by the supervisor, without a worker, as a very small technical task (`ae05894`): `ReadingTwo.parameters()` returns
`new LinkedHashSet<>(List.of("beta", "alpha"))`, an order that is not alphabetical and does not vary.

Checked: `./gradlew :daemon:test --tests '*RequestHandlerTest*'` green; with the `.sorted()` of
`RequestHandler.checkParameters` removed, `ofSeveralMissingParametersTheFirstByNameIsNamed` is red on two runs in a row
(the sort was put back). `bin/ci --fast`: format, build and test passed (cli 71, core 259, daemon 366, protocol 320,
shared 236, architecture 19, 0 failures).

Tokens:
- worker: none (done by the supervisor)
- reviewer: none (one-line test change, no review)
- supervisor: 59 calls, output 51229, input + cache_write 683743 (cache read 4569212), over the whole session of the
  refocus (the roadmap and the on-hold commit `a3575da` included, this ticket is a small part of it); exchanges with the
  user: 1 messages
