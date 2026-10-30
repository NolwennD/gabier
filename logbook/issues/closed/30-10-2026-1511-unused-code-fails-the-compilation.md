# Unused code fails the compilation

Opened: 30-10-2026 15:11
Depends on: 30-10-2026-1146-errors-reported-as-problem-objects (it touches the same modules)

JaCoCo finds code that no test runs, but only at the end of `check`. Error Prone finds unused code at compile time,
faster: its `UnusedMethod`, `UnusedVariable` and `UnusedParameters` checks only warn by default, and the copied
seed4j error package still has warnings.

Done when:
- [ ] The Error Prone checks for unused code are errors on the main code (in `gabier.java-module-conventions`, where
      NullAway is configured).
- [ ] The warnings they show today are fixed, the code copied from seed4j included, by removing the code or, with a
      reason, by an exception that says why.
- [ ] A test or a scratch change shows that an unused private method fails `compileJava` (shown once, then removed).
- [ ] `bin/ci` green.

## Resolution

Merged as `8c408d9` (rebased once over logbook-only commits on `main`, so `bin/ci` was not run again).

Checked by the worker's `bin/ci` (full, before the rebase): format 4s, build 24s, test 71s, native 123s, jdtls 95s, none
skipped. `gabier.java-module-conventions` makes `UnusedMethod` and `UnusedVariable` errors next to NullAway, on
`compileJava` only (the compile arguments show `-Xep:UnusedMethod:ERROR -Xep:UnusedVariable:ERROR`). Error Prone 2.51.0
has no `UnusedParameters` check: it is an alias of `UnusedVariable`, which covers the private fields, the local
variables and the parameters of private methods (and of the others when nothing overrides them). Warnings to fix: none,
the code showed no unused-code warning before (the seed4j error package included), so no code was removed and no
exception was needed. Scratch proof: a class with an unused private field, method, parameter and local made
`:shared:compileJava` fail with `[UnusedMethod]` and `[UnusedVariable]`, then it was deleted.

Worth remembering: Error Prone exempts variables named `unused...`, and `@SuppressWarnings("unused")` and
`("UnusedParameters")` also silence the checks. `quality.md` now frames both (`e91bd26`, from the review).

Review (one pass): no BLOCKING finding. The three suggestions were wording in `quality.md` (aliases of the suppression,
the parameters qualified like the build comment, the exemption of `unused` names), fixed by the supervisor in
`e91bd26`. The build still shows other warnings, not about unused code and not touched: 8 `UnnecessaryStringBuilder`,
3 `UnnecessaryLambda`, 1 `MissingSummary` (`Diagnose.java:21`).

Tokens:
- worker: 66049 (its last completion notification; it was resumed by messages)
- reviewer: 45097
- supervisor: 37065 output, 42837 input + cache write over 37 calls (cache read 8122586), supervisor session over the
  period of the ticket (from the launch of the worker; the other work of the session included); exchanges with the user:
  5 messages
