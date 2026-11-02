# `gabier organize-imports` removes a stale import without waiting or refusing

Opened: 31-10-2026 22:43

Found in the review of the daemon batch (`organize-imports-deletes-what-jdtls-cannot-resolve`). Since that ticket, an
`organize-imports` whose edit removes an import waits for the diagnostics of the file (the 5 s `QUIET` period) and refuses
it as `language-server-unresolved-imports` when jdtls reports an ERROR on a removed line (`JdtlsLanguageServer.java:482-505`).
Two cases hurt an agent: removing an unused import is the common case and now costs 5 s (`Untidy.java` of the
`ORGANIZE_IMPORTS` scenario); and a file with an unused import of a class that was deleted (`import demo.Gone;`) is refused,
where jdtls's deletion is right. An agent that deletes a class and fixes the files that imported it meets it at once.

Done when:
- [ ] The diagnostics are asked only when a removed import's simple name still appears in the file outside the import lines
      (only then can code that is used be lost); a scenario replayed on the real jdtls shows that removing an unused import
      does not wait.
- [ ] A file with an unused import of a class that does not exist is organized (the import removed), a scenario replayed on
      the real jdtls; an import that jdtls cannot resolve and whose name the file still uses is still refused, with the imports named, the file untouched,
      a scenario replayed on the real jdtls (the probe of the project, which checked it on `FakeServedMain`, goes: ticket
      `02-11-2026-0826`).
- [ ] `docs/commands/organize-imports.md` says what the command waits for and when it refuses. `bin/ci` green, coverage 100%.

## Resolution

Commit b1ce7f3 (rebased over text-only commits of `main`, no new `bin/ci`). `bin/ci` (worker, full, on the worktree): format 10s, build 18s, test 171s, native 148s, jdtls 576s, all passed (one earlier run stopped at `format` only, fixed with `hk fix --all`). Reviewed once: nothing blocking.

- Design: decided by the user on 02-11-2026 (the edit jdtls computes is taken without being written and judged before; no diagnostics when no used code can be lost; a refusal only for what loses code). `JdtlsLanguageServer.refuseTheImportsJdtlsCannotResolve` and `stillUsed`: a removed import is at risk only if its simple name appears as a whole word in the non-import lines of the original file; only then are the diagnostics asked, and only an ERROR on an at-risk line refuses. A removed star import counts as used.
- Measured on the real jdtls (second request, the first one on a fresh jdtls takes 2 to 26 s): 78 to 249 ms, where the old path waited at least the 5 s `QUIET`; the replay asserts it.
- Finding on jdtls, which changes the issue's second point: an import of a class that does not exist and that the file uses is kept by jdtls (empty edit), and so is an inaccessible single-type import. The only used code jdtls deletes is a star import of a package it reports as "not accessible" (`import java.sql.*;` in a module that cannot read `java.sql`). The refusal scenario uses that case; an unused import of a deleted class is removed (scenario `organize-imports-removes-an-unused-import-of-a-class-that-does-not-exist`).
- The probe of the project (`FakeServedMain`) is no longer the proof of the refusal: ticket `02-11-2026-0826` can delete it.
- Review suggestions: `02-11-2026-0920-organize-imports-used-name-review-suggestions.md` (on hold; the first finding, a `;` in a trailing comment, loses code in a rare case). Friction met by the worker: `02-11-2026-0920-organize-imports-does-not-offer-jdk-internal-classes.md`.

Tokens:
- worker: 163559 (`subagent_tokens`).
- reviewer: 63752 (`subagent_tokens`).
- supervisor: 11 calls, output 9270, input + cache write 15078 (cache read 2915332); covers the supervisor session over the period of the ticket (launch of the worker to now), other work included (questions on the probe and the imports, two issues opened); exchanges with the user: 1 message.
