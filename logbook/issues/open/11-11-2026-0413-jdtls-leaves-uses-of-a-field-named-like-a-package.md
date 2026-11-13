# jdtls leaves the uses of a field named like a package unrenamed

Opened: 11-11-2026 04:13
On hold: waits for the user, who decides whether the case is reported upstream to eclipse.jdt.ls; the guide already says it (`docs/improve.md`, "Rename a component of a record").

Met by the worker of `rename-a-record-component` (11-11-2026): with jdtls 1.63.0, `gabier rename` of a field or of a record component named `file`, `net`, `time` or `regex` leaves the uses by simple name (`return file;`) unrenamed, with a plain class field too; other names (`label`, `name`, `caption`) rename fully, and waiting 20 s gives the same result. The command answers with no warning, and `gabier references` on the same position does find the use, so an agent that renames such a name gets code that no longer compiles.

Done when:
- [ ] The cause is shown on the real jdtls with a throwaway replay (the names above are those of packages of the JDK, to check), on a plain class field and on a record component.
- [ ] Either a report for eclipse.jdt.ls is ready for the user to send (reproduction, cause), or the help of `rename` and `gabier help rename` name the limit, with the check an agent runs after the rename (`gabier diagnostics`); no check before the request (`.claude/rules/workflow.md`, "jdtls is never fought"). `bin/ci` passes.
