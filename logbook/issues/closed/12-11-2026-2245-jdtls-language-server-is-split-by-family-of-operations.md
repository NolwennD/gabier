# JdtlsLanguageServer is split by family of operations

Opened: 12-11-2026 22:45
Depends on: 12-11-2026-2147-affected-tests-can-be-followed-as-a-stream (closed; it changed the same class).

Tidying, no new behaviour. A worker that changes one operation of the adapter (`move`, `extract`, `create`) reads the
operations of that family only, not the whole `JdtlsLanguageServer` (about 1900 lines, every operation of the port
`StartedLanguageServer` and one lock for all). `JdtlsLanguageServer` stays the class that realises the port and holds the
state and the lock; each family of operations moves to a class of its own in the package, which the facade calls.

**Exception to the one-commit rule (decision of the user, 12-11-2026):** one commit per family, in one branch and one pull
request; the ticket is closed once they are all merged. Each commit builds and passes its own tests.

Done when:
- The operations of the port are grouped in families by what they share (the diagnostics and the build, the renames and the
  extractions, the moves, the code actions and the clean-ups, the creations, the inlining), and each family is a commit that
  moves it out of `JdtlsLanguageServer` without changing a line of its body but the access and the calls it needs; the
  worker states the families it chose in the resolution. The family that depends on the least goes first.
- Structure only: no test is deleted or weakened; `JdtlsLanguageServerTest` and the `integrationTest` pass after each
  commit, coverage stays 100 %, the architecture tests and Checkstyle stay green, and the ceilings of `JdtlsCouplingTest` do
  not rise.
- The lock and the order of the calls to jdtls are what they were: an operation that was `synchronized` is still serialised
  with the others, and a test (or the resolution, with the reason) says how.
- The rules of `.claude/rules/domain.md` hold for the new classes: the lowest visibility that compiles, and a name that says
  the technology and what the class holds.
- The moves of methods are done with `gabier` when a command exists; none does today (`gabier help --for="move methods to
  a new class"` abstains), so the worker does them as text edits and reports the missing command to the supervisor.

## Resolution

Merged from pull request 55 (branch `worktree-agent-a139151aa56ad000f`), eight commits, one per family, fast-forward on
`main`: `a2c181b` to `d9850eb`.

Families, in the order of the commits (the one that depends on the least first):
- `a2c181b` the creations (`JdtlsCreations`) and the project helpers they share (`JdtlsProject`);
- `9e0302b` the references (`JdtlsReferences`);
- `9423412` the code actions (`JdtlsCodeActions`) and the position helpers (`JdtlsPositions`);
- `4c6edb0` the inlining (`JdtlsInlining`);
- `4bdcd8e` the moves (`JdtlsMoves`);
- `942f795` the renames and the extractions (`JdtlsRefactorings`);
- `66192b4` the imports and the clean-ups (`JdtlsImports`);
- `d9850eb` the diagnostics and the build (`JdtlsDiagnostics`).

Lock: every public operation of the facade `JdtlsLanguageServer` is still `synchronized`, the families hold no lock and
are reached only from synchronized facade methods (or from its synchronized `published`, reentrant).
`JdtlsLanguageServerLockTest` fails when a public facade method loses `synchronized` or when a new public method is added
without it. It checks the modifier, not concurrent calls: opened as `13-11-2026-0218-the-lock-test-covers-published-and-answered`.

Checked: the check of the pull request, `bin-ci` (the full `bin/ci --all` on GitHub), green on `d9850eb`. The worker's own
`bin/ci` summary and its report were not available to the supervisor of this shipping (the worker had ended); the
evidence is the GitHub check and the neutral review.

Review (neutral, once): nothing to report. It compared the old bodies with the new files line by line: only access
(`private` to package-private) and calls (`JdtlsProject.*`, `JdtlsPositions.*`, `server.*`, `untaken.add` became
`server.queue`) differ; no test deleted or changed; no `@ExcludeFromGeneratedCodeCoverage` or `@SuppressWarnings` added.
No finding, so no suggestion left open.

Not reported: whether the worker met a missing `gabier` command for moving methods (its report was not available).

Tokens:
- worker: not recorded (its completion notification was not seen by this session);
- reviewer: 86862 (`subagent_tokens`);
- supervisor: this shipping only, from the start of the session: 14 calls, output 7891, input + cache_write 123115 (cache reads 794560); exchanges with the user: 2 messages. Not the period of the ticket.
