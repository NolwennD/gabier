# Rename the base package

Opened: 30-10-2026 02:30

On hold: it waits for the user to choose the new domain name, and it is meant as a test case for gabier's own rename
refactoring, so it is not picked up before gabier can do it.

`fr.craft` cannot stay: craft.fr is a domain that exists and is not available to the project. Replace the base of every
package and module name (`fr.craft.gabier…`) by the new base that the user gives. It touches every module, the build
(`group`, and the `mainClass` string of the native cli in `modules/cli/build.gradle.kts`, which no compiler checks), the
architecture tests (root package, allow-list constant) and the rules, so it runs alone. Move the files
with `git mv` so the history follows, and do the package rename through jdtls rather than by text replacement.

Done when:
- no `fr.craft` is left in the code, the build, the rules, the skills or the open issues; the closed issues are history
  and stay as they are;
- the folders match the new package names;
- `bin/ci` passes, the architecture tests included.

Depends on: architecture-tests-for-a-pure-java-core.
