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
- the imports a move writes follow the order of `organize-imports`; `bin/ci` passes.
