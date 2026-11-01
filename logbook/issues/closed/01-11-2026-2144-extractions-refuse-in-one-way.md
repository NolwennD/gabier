# The four extractions refuse a selection in one way

Opened: 01-11-2026 21:44

An agent that asks `extract-variable`, `extract-method`, `extract-constant` or `extract-field` for a selection jdtls
cannot extract gets the same kind of refusal from the four, whatever jdtls answered (decision of the user, 01-11-2026:
the variation of jdtls is absorbed in the adapter, the behaviour of gabier is homogeneous). Today jdtls answers an
`errorMessage` for some and `null` with no message for others (constant, field), and the problem keys and texts differ
by command (`language-server-not-extractable`, `language-server-not-an-expression`, `notAConstant`).

**Care**: one refusal contract for the four, texts written by gabier, never the message of jdtls. The adapter
(`JdtlsLanguageServer`) maps both jdtls answers (edit null with a message, null with none) to it; the core and the
cli do not know the difference. Also takes points 4 and 5 of
`01-11-2026-2128-extract-constant-and-field-review-suggestions.md` (they are about these texts: remove them from that
issue). Closing resolution: the issue `31-10-2026-2352-extract-refusals-carry-the-jdtls-message.md` is rejected by this
choice: move it to `closed/` with the reason.

Done when:
- [ ] The four extractions refuse with the same problem keys and parameters, and a text of gabier that says only what is
      known (a selection that is not an expression, or that cannot be extracted), the same text for the same case whatever
      the command; a test per case that fails if one command differs.
- [ ] A jdtls answer of `null` and a jdtls answer with an `errorMessage` give the same refusal, shown by the fake and by
      scenarios replayed on the real jdtls for the four commands.
- [ ] `docs/commands/extract-*.md` and the `help` list the same refusals; `bin/ci` passes.

## Resolution

Commit f35f2dd (rebased over text-only commits of `main`, no new `bin/ci`). `bin/ci` (worker, full, once): format 14s, build 5s, test 204s, native 181s, jdtls 514s, all passed. Reviewed once: nothing blocking.

- Why: the decision of the user (01-11-2026) is that the variation of jdtls is absorbed in the adapter and the behaviour of gabier is homogeneous. One key, `language-server-not-extractable`, one factory, `LanguageServerException.notExtractable(file, selection)`, the same parameters (`file`, `line`, `column`, `endLine`, `endColumn`) and a text written by gabier for the four commands. It replaces `language-server-not-an-expression` and the factories `notAnExpression` and `notAConstant`.
- The adapter: `JdtlsLanguageServer.extract()` throws it when jdtls answers null, a response error `InvalidRequest`, or an answer with no edit (with or without an `errorMessage`). The cli help of the four commands shares one constant.
- Finding: real jdtls 1.63 never answers an `errorMessage` for these four extractions (`GetRefactorEditHandler` builds one only for introduce-parameter and move); all the refusal scenarios come back null. The replays on the real jdtls show the null answer; the other answers are shown on the fake only (`JdtlsLanguageServerTest.ExtractingWhatJdtlsCannotExtract`, 4 commands × 4 answers), and the `Scenarios` Javadoc says so. Two scenarios were added (a method and a constant asked for the `return` keyword) so that the same selection is asked of the four.
- Review suggestions (shared text names "statements" for all four, a docs claim on replayed scenarios, wrapping): issue `01-11-2026-2223-extract-refusal-wording-and-docs.md`, on hold. The replay of null only is not a finding: it is the real behaviour.
- Unrelated friction met by the worker, not opened: `gabier diagnostics` on `JdtlsLanguageServerTest.java` answered a single truncated error, `The package fr.craft.gabier.daemon conflicts with a package accessible from another module:`. Left for the next time it shows in use.
- The issue `31-10-2026-2352-extract-refusals-carry-the-jdtls-message.md` is rejected by this choice (closed with the reason there).

Tokens:
- worker: 240830 (`subagent_tokens`).
- reviewer: 86745 (`subagent_tokens`).
- supervisor: 60 calls, output 42658, input + cache write 72227 (cache read 6669674); covers the supervisor session over the period of the ticket (launch of the worker to now), other work included (questions on ripwire, jdtls and Python, a rules cleanup, two issues opened); exchanges with the user: 11 messages.
