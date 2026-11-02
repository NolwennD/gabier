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
