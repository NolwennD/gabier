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
- [x] On a file whose imports jdtls cannot resolve, `organize-imports` refuses (problem object naming the unresolved
      imports) and writes nothing, or jdtls is given what it lacks; the choice is stated in the resolution (read the JDT LS
      documentation first: classpath of a source set, settings, `chooseImports`). A scenario replayed on the real jdtls
      shows it, red first.
- [x] A type declared in two packages is not silently skipped: the answer names the candidates (the command
      `java.action.organizeImports.chooseImports` is answered, or the refusal lists them). Same proof.
- [x] The warnings of `docs/commands/organize-imports.md` are replaced by the behaviour; the `fakeServed` filter of the probe is removed or justified.
- [x] `bin/ci` green, coverage 100%.

## Resolution

Merged as `2626672` (third commit of the daemon batch; figures of the agents in `30-10-2026-1504-missing-file-is-not-outside-the-project.md`).
`organize-imports` refuses, and writes nothing, in the two cases: a type that several packages declare
(`language-server-ambiguous-imports`, the candidates sorted by simple name) and an import jdtls cannot resolve
(`language-server-unresolved-imports`, naming the imports). The warnings of `docs/commands/organize-imports.md` are replaced by
the behaviour.

How jdtls is configured (JDT LS v1.63.0 source, read from the local clone): `JdtlsCapabilities.extended()` sends the extended
capabilities `advancedOrganizeImportsSupport` and `executeClientCommandSupport` in `initializationOptions.extendedClientCapabilities`
(`ClientPreferences.isAdvancedOrganizeImportsSupported`, `BaseInitHandler` around line 106). With them, jdtls sends
`workspace/executeClientCommand` `java.action.organizeImports.chooseImports` (`OrganizeImportsHandler.getChooseImportsFunction`;
`SourceAssistProcessor` wires the chooser only when the client declares them); without, an ambiguous type is skipped silently.
`JdtlsClient.executeClientCommand` answers `[]` (nothing chosen, as `OrganizeImportsHandler` reads it) and keeps the candidates
in `ImportChoices`; the adapter refuses with them. **Refusal rather than picking one**: the daemon cannot know which type the
agent means. **Refusal rather than giving jdtls the classpath of the `fakeServed` source set**: that changes how the Gradle
build models a source set, outside the daemon, and a refusal protects any project in the same state. The unresolved case: the
edit removes an import line and jdtls reports an ERROR on that line.

**`Done when` point 1 is met by the probe, not by a catalogue scenario**: a scenario project cannot produce "The package ... is
not accessible" without a classpath (a nonexistent package, and a module that does not read `java.sql`, were tried: jdtls kept
the import). The refusal is proven on the real checkout by `JdtlsProbeTest.checkImportsJdtlsCannotResolveAreRefused`: red with
the `fakeServed` filter removed (`1 would change`, jdtls deleted `FakeJavaLanguageServer`, `Scenario`, `Scenarios`), green with
the refusal. The proof depends on the layout of the repository (`modules/daemon/src/fakeServed/.../FakeServedMain.java`).
Accepted by the supervisor on the reviewer's condition that this is said here. The ambiguous case has its scenario
`organize-imports-of-a-type-two-packages-declare`, red first (jdtls answered `Optional.empty`), replayed on the real jdtls.

Other proofs: `JdtlsLanguageServerTest$OrganizingImports` (six new tests), `JdtlsClientTest`, `ImportChoicesTest`,
`JdtlsCapabilitiesTest`, `JdtlsSessionTest`, `SelfCheckTest`, `OrganizeImportsTest`. The worker's `bin/ci` is in the first issue
of the batch. Not red first: the daemon unit tests were written after the code.

Review: nothing blocking. Findings, opened:
- the wait for diagnostics (5 s) on every removal of an import, and a refusal for an unused import of a deleted class: issue
  `31-10-2026-2243-organize-imports-asks-diagnostics-only-when-it-can-lose-code.md` (a real use, not on hold);
- the scenario pins the `sun.util.calendar.*` candidates, which depend on the JDK (`java.completion.filteredTypes` to check):
  issue `31-10-2026-2243-daemon-batch-review-suggestions.md` (on hold).
Friction while using gabier: the installed `gabier` (17:31) had no `organize-imports`, so the worker edited two imports by
hand; `mise run install` from `main` after the merge.
