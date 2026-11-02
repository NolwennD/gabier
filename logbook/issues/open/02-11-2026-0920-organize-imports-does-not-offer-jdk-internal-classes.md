# gabier organize-imports does not choose between a JDK-internal class and the public one

Opened: 02-11-2026 09:20

Friction met while using gabier (worker of `31-10-2026-2243-...`). `gabier organize-imports` on a file of the daemon module that uses
`Pattern` with no import is refused as ambiguous: jdtls offers `com.sun.org.apache.xalan.internal.xsltc.compiler.Pattern` next to
`java.util.regex.Pattern`, and gabier refuses with both as candidates, so the agent adds the import by hand and loses the point of the
command. Reproduce: use `Pattern` in a file of `modules/daemon` with no import, run `gabier organize-imports <file>`.

**Care**: jdtls is configured, not fought: start from the JDT LS documentation of the settings that filter the candidates
(`java.completion.filteredTypes`, `java.completion.favoriteStaticMembers`) and say in the resolution which one gives the behaviour; the
adapter stays a relay (`JdtlsSettings`, `JdtlsCapabilities`). A genuine ambiguity (two public classes) is still refused.

Done when:
- [ ] A scenario replayed on the real jdtls: a file that uses `Pattern` with no import, in a project where only the JDK-internal class and
      `java.util.regex.Pattern` are candidates, is organized with `java.util.regex.Pattern`; two public candidates are still refused.
- [ ] `docs/commands/organize-imports.md` says how the candidates are filtered. `bin/ci` passes.
