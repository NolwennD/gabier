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
