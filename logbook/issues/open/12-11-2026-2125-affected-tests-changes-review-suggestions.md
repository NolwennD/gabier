# The changes of affected-tests hold when calls overlap and say what they replaced

Opened: 12-11-2026 21:25
On hold: findings of the review of `gabier affected-tests` with no file (PR 50) that break no `Done when` point and no use met
yet; lifted when a project shows one (two agents calling the same daemon at the same moment is the likeliest).

The review found cases of `JdtlsLanguageServer.builtWithChanges` and `ChangedBuild`, of the cli `AffectedTests` and of the
scenario `ChangeScenarios.A_RENAME` that no test pins or that the page does not say.

Done when:
- Two calls of `affected-tests` with no file that overlap in time are answered as the page says: each change is answered to
  one of them only, or the page says that overlapping calls may both be answered; a test with two overlapping calls covers it
  (`AffectedTestsChangedMethodTest` calls one after the other today).
- `gabier affected-tests` given an operand list that came out empty (a glob that matches nothing, `$(git diff --name-only)`
  with no output) is told apart from the call with no file: the page says what happens, or the cli refuses it; a test covers it.
- A call whose wait for jdtls timed out and is made again does not list a file twice or keep it in the list after it was
  answered: a test of `JdtlsBuiltProjectTest` covers a change recorded before the timeout, or the page says what happens.
- The replay of a rename (`ChangeScenarios.A_RENAME`) is made by a `gabier rename` run through the daemon on the real jdtls, or
  the page and the daemon page stop saying that a rename was observed and say what was.
