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

## Resolution

Closed without doing the work (decision of the user, 13-11-2026): the split adds public types and gives only tidiness.

- **What was measured, before any move** (read-only; types used by another class of the package, comments left out):
  - Today `eu.gabier.daemon` has 88 files: `Main` is the only type declared `public`, 86 are package-private, the other file is
    `package-info.java`. The `module-info` has no `exports`, so nothing of the daemon is reachable from another module, `Main`
    included (the JVM starts it by name). The `opens` of the package are for the reflection of Gson and LSP4J only.
  - `HexagonalArchTest.packagesShouldBeFreeOfCycles` forbids a cycle between packages at any depth. A split into `methods`,
    `jdtls`, the watch of the files and the server gave cycles (the `*Method` classes use `Peer` and `DaemonErrorKey`, which
    `DaemonServer` also builds on; `Watching` uses `DaemonMethod.Outcome`; the adapter of jdtls uses `DaemonErrorKey`), so what
    the sub-packages need had to go down to a package of its own.
  - Every split measured ends near 15 public types, even with a registry for the 26 `*Method` classes (29 types public without
    it, 2 with it) and an entry point for the adapter of jdtls (11 public without it, about 3 with it): 15 to 17 for a split in
    the vocabulary of the hexagon (`application`, `primary`, `secondary`), about 15 for `base`, `methods`, `jdtls`. Against 1 now.
- **Why it was rejected:** the `public` of a sub-package marks a frontier between packages of the daemon, not an API towards
  another module, which already sees nothing. The only gain is the layout of 88 files, at the price of about 15 types made
  public, 75 test classes to move, the `opens` of the reflection to extend and a number of types that other packages of the daemon
  could then lean on. A consumer outside the module cannot couple to a detail; the coupling to watch is between the packages of the
  daemon, which one package keeps impossible by visibility.
- **What it replaced and what stays:** nothing replaces it. If the flat layout becomes a problem, a `package-info.java` that
  names the roles (the daemon methods, the adapter of jdtls, the following of the files, the server) gives the map with no change of
  visibility. The ticket `12-11-2026-2245-jdtls-language-server-is-split-by-family-of-operations` no longer depends on this one.
  The warning `OperatorPrecedence` that the ticket had taken in (`Main.java`) is done by the supervisor, without a ticket.
- **Process note:** the supervisor gave figures twice that the next measure corrected (about 7 public types for a split in two
  packages, which the cycle rule makes about 15); the user chose on the first figure and decided again on the corrected one.

Tokens:
- supervisor: 40 calls, output 61171, input + cache write 81278 (cache read 11557429 apart); covers the supervisor session from the
  start of the work to this closing, the discussion of the architecture included, no worker and no reviewer; exchanges with the
  user: 8 messages
