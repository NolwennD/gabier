# gabier cleanup is an engine-specific capability, not part of the core

Opened: 02-11-2026 12:42

The user's remark (02-11-2026): the clean-ups are very Eclipse (the Eclipse JDT clean-up profiles); IntelliJ has no such thing and NetBeans
is unknown. Today the 19 identifiers of jdtls (`addFinalModifier`, `stringConcatToTextBlock`...) are an enum of the domain (`CleanUp`),
validated in the core: a second Java language server would find them in the domain. This reverses the choice of 02-11-2026 morning
(validation in the core), taken before the coupling question.

**Care**: the identifiers become opaque text for the core; the adapter (`daemon`) knows the ones jdtls accepts and refuses an unknown one
with the same problem, `editing-unknown-clean-up`, naming the identifiers the server knows. A server with no clean-ups answers a
refusal that says the capability is not offered (`language-server-not-supported`), and `gabier help` / `docs/commands/cleanup.md` say
that `cleanup` is a capability of the Eclipse server (the list of the identifiers is the one of the server, not of gabier). `organizeImports`
stays refused as an identifier (the command `organize-imports` does it). The behaviour for jdtls does not change.

Done when:
- [ ] `CleanUp` no longer exists in `modules/core` (the port takes the identifiers as text); the adapter validates them against its list
      (tests red first: an unknown identifier is refused before the server is asked, with the known ones named; `organizeImports` pointing to
      `organize-imports`); the scenarios of `CleanUpScenarios` still replay on the real jdtls.
- [ ] A fake server that offers no clean-ups refuses `cleanup` with the not-supported problem, shown by a test of the core.
- [ ] `docs/commands/cleanup.md` and `help` say that the clean-ups are those of the Eclipse server. `bin/ci` passes.

## Resolution

Rejected, not done. The user decided on 02-11-2026 that `CleanUp` stays in the core as it is: the coupling to the Eclipse clean-ups is wanted. What is to come is a way to say so in the code, and to count it: issue `02-11-2026-1249-intended-coupling-is-declared-in-the-code.md` (on hold).
