# The daemon package is split by role, with a minimal public surface

Opened: 12-11-2026 23:05
Depends on: 12-11-2026-2147-affected-tests-can-be-followed-as-a-stream (it adds classes to the same package).

Tidying, no new behaviour. `eu.gabier.daemon` is one flat package of about 85 files, of which one is public (`Main`): a
reader cannot tell what the daemon serves from what implements it, and nothing stops a class from leaning on the inside of
another. The package is split by role into sub-packages, and each one shows the **least possible** to the others (the code
that serves the daemon methods, the adapter of jdtls, the following of the files on disk, the server and its connections are
the roles the worker checks against the dependencies). Nothing is exposed outside the module (the `module-info` exports
nothing); what a client reaches is the protocol.

**Exception to the one-commit rule (decision of the user, 12-11-2026):** one commit per package, in one branch and one pull
request; the ticket is closed once they are all merged. Each commit builds and passes its own tests. This ticket comes before
`12-11-2026-2245-jdtls-language-server-is-split-by-family-of-operations`, so the surface of `jdtls` is thought once.

Done when:
- Before any move, the worker measures which classes of each role are used by the others, and writes in the resolution the
  types it keeps public, one line of reason each. A package shows an entry point (a registry, a factory, a port), not its
  classes: the `*Method` classes stay package-private and `DaemonServer` knows one type of their package.
- A public type of a sub-package of the daemon that is not on an explicit list fails the build: the check
  `HexagonalArchTest.Visibility` announced in `.claude/rules/domain.md` is written (a public top-level type that no class of
  another package depends on fails), and the rule says it is now checked; a second check keeps the list of the public types of
  the daemon's sub-packages, so adding one shows in the diff.
- Behaviour is unchanged: no test deleted or weakened, `JdtlsLanguageServerTest`, the `integrationTest` and `:daemon:selfCheck`
  pass after each commit, coverage stays 100 %, Checkstyle and the architecture tests stay green, and the ceilings of
  `JdtlsCouplingTest` do not rise.
- The `opens` of the `module-info` (the reflection of LSP4J and Gson) still reach the classes that need them; the daemon runs on
  the module path as before.
- The classes are moved with `gabier move` (it rewrites the `package` lines, the imports and the references), `gabier
  diagnostics` is run after each move for the package-private members it cannot see, and the friction met is reported to the
  supervisor.
- `Main` moves with the split, and the warning `OperatorPrecedence` that `./gradlew :daemon:compileJava` prints for its
  condition on the arguments is gone: the condition is extracted into a method named for the rule (with `gabier
  extract-method`), not parenthesised or commented.
- The page of the daemon (`docs/daemon.md`) and `bin/ci.d/native/` still name what exists; the guide and the skills that name
  a class of the daemon are corrected.
