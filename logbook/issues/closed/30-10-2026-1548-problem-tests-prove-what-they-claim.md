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

## Resolution

Merged as `2ce7e7f` (first commit of a batch of four issues of `protocol` and `cli`, one worktree, one `bin/ci`).

Checked by the worker's `bin/ci --all` on the last commit before the rebase (format 8s, build 5s, test 100s, native 171s with the binary really rebuilt, jdtls 137s; each commit had passed `bin/ci --fast`), then, after the rebase on `main`, `bin/ci --stage test` on the head: 94s, cli 66, core 259, daemon 271, protocol 251, shared 236, architecture 19, no failure.
`GabierTest.Failure.runReportsAnExceptionThatNobodyExpectedAsAnInternalErrorInsteadOfLettingItEscape` passes an environment whose `get` throws and expects exit 1, the generic internal-error text and an empty stdout; with the catch narrowed to `UsageException` the exception escapes and it fails (mutation shown once, restored). `ProblemMapperTest.aTechnicalErrorHasTheInternalErrorKeyAndKeepsItsOwnMessageAsTheDetail` says in its name and a comment that the message of a `GeneratorException` is gabier's own by design; `ProblemTest.keepsItsCategoryOutOfTheJson` asserts the JSON has no `category` or `status` member. `bin/ci.d/40-native` compares the whole JSON of `--json --help` with `equals`, on a text block ending in `\`, so it holds no trailing newline.

Not reviewed: small ticket (tests and the native stage script, no behaviour change), by the rule of `ship` step 1.

Tokens:
- worker: 153359 (the whole batch of four issues; its last completion notification)
- supervisor: 63866 output, 120906 input + cache write over 46 calls (cache read 16118547), supervisor session over the
  period of the batch (from the launch of the worker; three other workers and the rest of the session are included, so
  the figure is shared); exchanges with the user: 0 messages
- reviewer: none (not reviewed)
