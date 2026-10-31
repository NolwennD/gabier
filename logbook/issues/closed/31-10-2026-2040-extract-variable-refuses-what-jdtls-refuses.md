# `gabier extract-variable` refuses what jdtls refuses, with the right reason

Opened: 31-10-2026 20:40

Found in the review of `gabier-extracts-a-variable`. An agent that selects a statement or a `void` call gets an internal
error instead of the refusal `language-server-not-an-expression`: `JdtlsLanguageServer.java:271-276` turns a
`RefactorEdit(edit=null, errorMessage=...)` of jdtls into a `JdtlsException` ("without an edit"), and the test
`failsWhenJdtlsGivesNoEdit` pins it. The documentation (`docs/commands/extract-variable.md`) already claims the refusal for a statement and a `void`
call; only a `return` keyword is scripted and replayed.

Done when:
- [x] A selection that is a statement, and one that is a `void` call, are refused as `language-server-not-an-expression`,
      nothing written: contract scenarios replayed on the real jdtls (skill `add-scenario`), and the test that pinned the
      internal error is replaced on purpose (behaviour change, said in the resolution).
- [x] A name equal to the one jdtls proposes (`string`) is replayed on the real jdtls (today only on the fake).
- [x] A name that collides with an existing local (`name` in `greet(String name)`) is replayed on the real jdtls, and the
      resolution says which refusal comes back (`invalid-name` or another one).
- [x] The page `docs/commands/extract-variable.md` says only what the scenarios prove. `bin/ci` green, coverage 100%.

## Resolution

Merged as `27a7122` (second commit of the daemon batch; figures of the agents in `30-10-2026-1504-missing-file-is-not-outside-the-project.md`).
A statement and a call of a `void` method are refused as `language-server-not-an-expression`: jdtls answers no edit and an
`errorMessage`, which the adapter now turns into the refusal. Behaviour change on purpose: `failsWhenJdtlsGivesNoEdit`, which
pinned the internal error, is replaced by `refusesAsNotAnExpressionWhenJdtlsGivesNoEditButSaysWhy`.

Proofs: scenarios `extract-a-variable-from-a-statement` and `extract-a-variable-from-a-void-call`
(`NOT_AN_EXPRESSION`), `extract-a-variable-to-the-name-jdtls-proposes` (name `string`, the expected edit) and
`extract-a-variable-to-a-name-in-use`, all replayed on the real jdtls; `ExtractVariableTest.refusesAWholeStatement`,
`refusesACallOfAVoidMethod`, `keepsTheNameJdtlsWouldHaveProposedWhenThatIsTheNameAskedFor`, `refusesANameThatTheMethodAlreadyUses`;
`JdtlsLanguageServerTest` for the check below.

**A collision of the name is not refused by jdtls**: `RenameHandler` throws at FATAL severity only, so for a name that is
a parameter (`name` in `greet(String name)`) it answers a corrupt merged edit (`name= greeting + ", " + name;`, the space
lost, the next line mangled). The refusal `invalid-name` comes from a check of gabier, `leavesTheDeclarationAsItWas`: after
the rename, the line of the declaration must be unchanged but for the name. Only the case of a parameter name is proven; the
doc says what is proven. The check is weak (first line after the name only): suggestion below.

Review: nothing blocking. Suggestions, in issue `31-10-2026-2243-daemon-batch-review-suggestions.md` (on hold): compare the
whole text after the rename with the text before; say "not tested" of the other collisions; a scenario with an earlier local.
