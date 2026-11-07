# gabier move keeps the nested and qualified names it rewrites right

Opened: 02-11-2026 20:56

Met while renaming the base package (`rename-the-base-package`), the moves of 02-11-2026 leave code that does not compile
or still names the old package, with exit 0:
- a use of a nested type through its outer type loses the outer name: `gabier move` of the `protocol` package rewrote
  `RpcResponse.Success` as `eu.gabier.protocol.Success` (`modules/cli/.../Invocation.java`, 3 places) and
  `RpcResponse.Failure` as `eu.gabier.protocol.Failure` in 15 tests of `modules/daemon/src/test`, the
  `import …RpcResponse` kept;
- fully qualified names in code (`new fr.craft.gabier.editing.domain.Range(...)` in `JdtlsLanguageServerTest`,
  `JdtlsCleanUpTest`, `JdtlsMoveToPackageTest`) are not rewritten;
- the new imports are not in the order of the formatter.
Start from the settings of the move refactoring of jdtls (qualified names, textual occurrences) before any workaround.

Done when:
- a move of a class that has a nested type used as `Outer.Inner` elsewhere keeps `Outer.Inner`; a scenario and its replay
  show it;
- a fully qualified name of a moved class in Java code is rewritten; a scenario and its replay show it;
- whether string literals are rewritten is said in `docs/commands/move.md`;
- the imports a move writes follow the order of `organize-imports` (amended by the user, 07-11-2026: jdtls does not sort
  them and is not fought; `docs/commands/move.md` and `gabier help move` say to run `gabier organize-imports` on the
  modified files); `bin/ci` passes.

## Resolution

Done in 2f186da.

- The bugs of the issue no longer reproduce on the real jdtls (checked with the installed gabier and a scratch project): a
  nested type used as `Outer.Inner` keeps its outer name, in one module and across modules (type, `new`, `instanceof`,
  cast, method reference, `case` pattern, import of the nested type); a fully qualified name in code is rewritten
  (a dry run moving `Range` rewrote `new eu.gabier.editing.domain.Range(` in the daemon tests); a dry run of the whole
  `protocol` package gave no `Success` or `Failure` without its outer type. They are now locked by the scenario
  `move-a-class-with-nested-types-that-another-module-uses` (replayed, `:daemon:jdtlsProbe`), which also holds a comment
  and a string with the old name: they stay unchanged, and `docs/commands/move.md`, `gabier help move` and
  `docs/improve.md` say so.
- What gives the behaviour: the `java/move` request with `moveResource` and `updateReferences` true; no `java.*`
  setting is involved. No source change.
- **Decision of the user (07-11-2026)**: jdtls does not sort the import block it writes (observed: `import demo.aaa.Thing;`
  between `demo.Outer.Success` and `demo.Sibling`). The point "the imports follow the order of `organize-imports`" is
  amended to documentation: jdtls is not fought (`workflow.md`), and organizing the text before applying would drop the
  imports of the type at its new package. The docs tell to run `gabier organize-imports` on the modified files. Whether a
  setting of jdtls sorts them was not proven in its source.
- The batch was reviewed once. One finding showed a `Done when` point of `move-keeps-nested-and-qualified-names` unmet (the order of the imports); the user decided on 07-11-2026 to document it and amend the point. The other findings are in `move-keeps-the-visibility-of-an-exported-package` and `move-and-references-review-suggestions`; the bug met on the way is `move-on-a-root-with-two-eclipse-projects`. `bin-ci` of pull request 21: green. `bin/ci --all` of the worker: format, build, test (cli 885, core 723, daemon 974, protocol 397, shared 236, architecture 19), release-install, native, jdtls all passed. No coupling ceiling raised.

Tokens (one batch of three issues, shared by the three resolutions):
- worker: 329256
- reviewer: 89686
- supervisor: 33 calls, 26779 output, 177000 input + cache write (5401473 cache reads), over the period from the launch of
  the worker to the merge, the other work of the period included; exchanges with the user: 4 messages
