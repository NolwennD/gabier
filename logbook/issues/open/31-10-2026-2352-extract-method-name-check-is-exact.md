# The name check of `extract-method` is exact, and its fake answers as the real jdtls does

Opened: 31-10-2026 23:52

Found in the review of `gabier-extracts-a-method`. `JdtlsLanguageServer.refuseASignatureInUse` refuses a name that the class already
has with the parameters of the new method. It picks the first symbol whose name starts with `extracted(`
(`JdtlsLanguageServer.java:456`) and compares the `containerName` (`:456-469`), so it refuses wrongly or misses: `Outer` declares
`void extracted(String)` before `class Inner`, and extracting from `Inner` (jdtls proposes `extracted(int)` there) is checked against
`Outer.extracted(String)`; `A.Inner.doubled(int)` and an extraction named `doubled` into `B.Inner` give a spurious `invalid-name`
(jdtls's container name is the simple name of the parent, and empty for an anonymous class). The labels the fake scripts
(`doubled(int) : String`, a "differs only by return type" case, the `signatureOf` branch for ` : `) are not what the real server
gives: `DocumentSymbolHandler` uses `ALL_DEFAULT`, so the label is `doubled(int)` or `doubled(T) <T>`, with no return type: a
fake that promises what the server does not do (`.claude/rules/workflow.md`, "Fake it until you run it").

Done when:
- [ ] The check selects the symbol whose `location.range` contains the offset of the new method, not the first by prefix or by
      container; tests: the `Outer`/`Inner` case above and two nested classes of the same simple name.
- [ ] The labels of the fake and the Javadoc follow the real format (`name(params)`, `name(T) <T>`); the dead ` : ` branch and the
      "return type only" case are removed; a scenario replayed on the real jdtls shows the format.
- [ ] A scenario replayed on the real jdtls shows that the rename accepts the same name with other parameter types (an overload);
      `docs/commands/extract-method.md` says it is replayed.
- [ ] `introducedName` and the Javadoc of the renaming (`JdtlsLanguageServer.java:368, 395`) say "method" on the method path
      (use `extraction.extracted`), with a test.
- [ ] `bin/ci` green, coverage 100%.
