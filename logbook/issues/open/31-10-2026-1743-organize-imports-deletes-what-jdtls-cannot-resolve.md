# `gabier organize-imports` never deletes an import it cannot judge

Opened: 31-10-2026 17:43

Found while making `organize-imports` (worker's report). An agent that runs `gabier organize-imports` on a file jdtls
cannot fully resolve loses code. Case 1: `FakeServedMain.java` (the `fakeServed` source set, whose classpath jdtls does not
have): jdtls reports `The package fr.craft.gabier.editing is not accessible` and answers an edit that deletes three valid
imports (`FakeJavaLanguageServer`, `Scenario`, `Scenarios`). Case 2: a type that two packages declare (`List` and `Date`
with no import): jdtls sends `java.action.organizeImports.chooseImports` to the client, which gabier does not answer, so
the type is silently left unimported (`ArrayList` plus `Date`: `ArrayList` is added, `Date` stays unresolved, no message).
`docs/commands/organize-imports.md` warns about both, the probe skips that source set. Reproduce case 1: remove the `/src/fakeServed/` filter of
`JdtlsProbeTest.checkImportsOfTheProjectAreInOrder`.

Done when:
- [ ] On a file whose imports jdtls cannot resolve, `organize-imports` refuses (problem object naming the unresolved
      imports) and writes nothing, or jdtls is given what it lacks; the choice is stated in the resolution (read the JDT LS
      documentation first: classpath of a source set, settings, `chooseImports`). A scenario replayed on the real jdtls
      shows it, red first.
- [ ] A type declared in two packages is not silently skipped: the answer names the candidates (the command
      `java.action.organizeImports.chooseImports` is answered, or the refusal lists them). Same proof.
- [ ] The warnings of `docs/commands/organize-imports.md` are replaced by the behaviour; the `fakeServed` filter of the probe is removed or justified.
- [ ] `bin/ci` green, coverage 100%.
