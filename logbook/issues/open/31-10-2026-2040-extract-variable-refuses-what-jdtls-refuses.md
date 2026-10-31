# `gabier extract-variable` refuses what jdtls refuses, with the right reason

Opened: 31-10-2026 20:40

Found in the review of `gabier-extracts-a-variable`. An agent that selects a statement or a `void` call gets an internal
error instead of the refusal `language-server-not-an-expression`: `JdtlsLanguageServer.java:271-276` turns a
`RefactorEdit(edit=null, errorMessage=...)` of jdtls into a `JdtlsException` ("without an edit"), and the test
`failsWhenJdtlsGivesNoEdit` pins it. The README (`README.md:243`) already claims the refusal for a statement and a `void`
call; only a `return` keyword is scripted and replayed.

Done when:
- [ ] A selection that is a statement, and one that is a `void` call, are refused as `language-server-not-an-expression`,
      nothing written: contract scenarios replayed on the real jdtls (skill `add-scenario`), and the test that pinned the
      internal error is replaced on purpose (behaviour change, said in the resolution).
- [ ] A name equal to the one jdtls proposes (`string`) is replayed on the real jdtls (today only on the fake).
- [ ] A name that collides with an existing local (`name` in `greet(String name)`) is replayed on the real jdtls, and the
      resolution says which refusal comes back (`invalid-name` or another one).
- [ ] The README says only what the scenarios prove. `bin/ci` green, coverage 100%.
