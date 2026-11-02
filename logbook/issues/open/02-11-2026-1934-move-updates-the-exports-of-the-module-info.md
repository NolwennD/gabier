# gabier move updates the exports of the module-info

Opened: 02-11-2026 19:34

Met while diagnosing `move-rewrites-references-in-other-modules`: a move of every class of a package of a JPMS module
leaves the `exports` (and `opens`) line of its `module-info.java` on the old name, which no longer exists, so the module
stops compiling for its dependents. Reproduce: in `modules/shared`, `gabier move --dry-run
modules/shared/src/main/java/fr/craft/gabier/shared/error/domain/*.java eu.gabier.shared.error.domain` shows no change of
`modules/shared/src/main/java/module-info.java`. Start from what jdtls's move refactoring offers for the module-info.

Done when:
- a move that empties an exported or opened package renames it in the `exports`/`opens` lines of the module-info; a move
  that leaves classes in it changes nothing there; a scenario and its replay show both;
- `bin/ci` passes.

Depends on: move-rewrites-references-in-other-modules.
