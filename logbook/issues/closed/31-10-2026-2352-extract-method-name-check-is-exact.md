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

## Resolution

Commit 1934621. `bin/ci` (worker, full, after the fix of the review, on the worktree): format 11s, build 4s, test 199s, native 38s, jdtls 420s, all passed. Reviewed once: one blocking finding (the replay did not show the label format, `ExtractedMethodSymbolsTest` added), fixed and checked by `bin/ci`, not reviewed again.

- Finding on the real jdtls (v1.63.0) that changes the issue: the position of the name that `java/getRefactorEdit` introduces is the **call**, not the declaration (asked (5,8), declaration (9,17)), so "the symbol whose `location.range` contains the offset of the new method" could not be implemented as written. `refuseASignatureInUse` now asks `textDocument/definition` at the introduced name (one more request, only when the name asked differs from the one jdtls proposed), takes the method symbol whose name range holds the definition, and its class is the first type listed after it whose name is its container (the outline is post-order: members, then their type; a generic type's `<T>` is stripped). A missing definition is a `JdtlsException`. Two nested classes of the same simple name are two classes.
- Labels: `name(params)` or `name(T) <T>`, no return type (`DocumentSymbolHandler`, `ALL_DEFAULT`); the dead ` : ` branch and the "return type only" case of the fake are removed. `ExtractedMethodSymbolsTest` (`jdtlsProbe`, real jdtls) asserts the call position, the definition on `private int extracted(int[] values)` and the exact labels of the methods of the overload project; shown red once with `print(int[]) : void`.
- Overload: scenario `EXTRACT_METHOD_TO_THE_NAME_OF_AN_OVERLOAD` (`values.length` in `Totals.print`, name `doubled`, next to `doubled(int)`), replayed on the real jdtls. `introducedName` says "method" on the method path.
- The worker showed that 9 of the 27 new or changed tests of `ExtractingAMethod` fail on the old production code; the others are regression guards.
- Not covered by a replay: an anonymous class (recognised by an empty container name and a label that does not start like a name: wrong on the real server, see the suggestions). Review suggestions: `02-11-2026-1628-extract-method-name-check-review-suggestions.md` (on hold).

Tokens:
- worker: 219763 (`subagent_tokens` of its last notification; the worker was resumed twice: after the GO and after the review).
- reviewer: 78093 (`subagent_tokens`).
- supervisor: 24 calls, output 21939, input + cache write 35802 (cache read 9901335); covers the supervisor session over the period of the ticket (launch of the worker to now), other work included (the pause of 14:30, a reinstall of gabier); exchanges with the user: 2 messages.
