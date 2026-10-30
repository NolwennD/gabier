# Rename the base package

Opened: 30-10-2026 02:30

`fr.craft` cannot stay: craft.fr is a domain that exists and is not available to the project. Replace the base of every
package and module name (`fr.craft.gabier…`) by the new base that the user gives before the ticket starts. It touches
every module, the build (`group`), the architecture tests (root package, allow-list constant) and the rules, so it runs
alone: after the architecture tests, before the cli. Move the files with `git mv` so the history follows, and prefer a
package rename through jdtls to a text replacement when it can do it.

Done when:
- no `fr.craft` is left in the code, the build, the rules, the skills or the open issues; the closed issues are history
  and stay as they are;
- the folders match the new package names;
- `bin/ci` passes, the architecture tests included.

Depends on: architecture-tests-for-a-pure-java-core.
