# Rename the base package

Opened: 30-10-2026 02:30

`fr.craft` cannot stay: craft.fr is a domain that exists and is not available to the project. Replace the base of every
package and module name (`fr.craft.gabier…`) by `eu.gabier…` (`gabier.eu` is available, decision of the user, 02-11-2026):
`fr.craft.gabier.core` becomes `eu.gabier.core`, the Gradle `group` becomes `eu.gabier`. It touches every module, the build
(`group`, and the `mainClass` string of the native cli in `modules/cli/build.gradle.kts`, which no compiler checks), the
architecture tests (root package, allow-list constant) and the rules, so it runs alone. Move the files
with `git mv` so the history follows, and do the package rename through gabier (`gabier move`, `gabier rename`) rather than by text replacement; what gabier
cannot do is a text edit and a friction reported to the supervisor.

Done when:
- no `fr.craft` is left in the code, the build, the rules, the skills or the open issues; the closed issues are history
  and stay as they are;
- the folders match the new package names;
- `bin/ci` passes, the architecture tests included.

Depends on: architecture-tests-for-a-pure-java-core, move-rewrites-references-in-other-modules, move-removes-the-folders-it-empties.

## Resolution

Done in `f2c42c5`: every package and JPMS module of `fr.craft.gabier` is now `eu.gabier` (the user's choice of
02-11-2026: `gabier.eu` is available, craft.fr is not), the Gradle `group` is `eu.gabier`. The Java packages were moved by
23 calls of `gabier move`, one per (source set, package), which rewrote the `package` lines, the imports and the references
across modules and removed the folders they emptied; `gabier diagnostics` was clean on the dependent modules after each
module. 371 files show as renames; the 5 `package-info.java` are too small for git to pair them and show as added and
deleted.

A first attempt (02-11-2026) stopped: `gabier move` rewrote no module but the moved one. The cause was the build
(`extra-java-module-info` without `skipLocalJars`, closed issue `move-rewrites-references-in-other-modules`), so this
ticket waited for it and for `move-removes-the-folders-it-empties`, and the rename was redone from `main`.

Text edits, where gabier has no operation: the `module-info.java` (names, `requires`, and the `exports`/`opens` that
`move` does not rewrite yet, issue `move-updates-the-exports-of-the-module-info`), the build strings (`settings.gradle.kts`,
`mainClass`/`mainModule`, `build-logic`), `config/checkstyle/suppressions.xml`, string literals (`GeneratorException`,
`ChangesCommandContract`, `FakeOnlyReasonTest`, `SelfCheckTest`, `MainTest`, the root packages of the architecture
tests), and the rules, skills and the one open issue that named the old path. What jdtls left in Java code was also
fixed by text and opened as `move-keeps-nested-and-qualified-names`: `RpcResponse.Success`/`Failure` rewritten without
their outer type (16 files), fully qualified names not rewritten (3 tests), imports out of the formatter's order.

Checked by the worker's `bin/ci --all` (no stage skipped): format, build, test (cli 598, core 693, daemon 866, protocol
366, shared 236, architecture 19, 0 failures), native and jdtls passed, coupling ceilings unchanged. The grep of
`fr.craft` outside the closed issues and `docs/superpowers/` finds only this issue.

Not reviewed: small ticket, a rename and a move with the behaviour untouched (`.claude/skills/ship/SKILL.md`, step 1).

Tokens:
- worker: 181 605 (first attempt 79 138, second 102 467)
- reviewer: none (not reviewed)
- supervisor: 30 calls, 21 367 output, 35 709 input + cache write (4 569 870 cache read apart); the supervisor session from the launch of the second worker to the close, the other work of the period included (the issues of the GitHub publication, the CI and the releases); exchanges with the user: 3 messages (a floor)
