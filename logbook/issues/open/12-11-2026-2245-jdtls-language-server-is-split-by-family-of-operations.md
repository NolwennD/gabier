# JdtlsLanguageServer is split by family of operations

Opened: 12-11-2026 22:45
Depends on: 12-11-2026-2147-affected-tests-can-be-followed-as-a-stream (it changes the same class),
12-11-2026-2305-the-daemon-package-is-split-by-role-with-a-minimal-surface (the class lands in its package first, and
its public surface is decided there).

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
