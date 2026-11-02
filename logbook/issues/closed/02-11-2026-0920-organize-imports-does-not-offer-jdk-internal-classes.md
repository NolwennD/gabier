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

## Resolution

Commit f0ebed3 (rebased over text-only commits of `main`, no new `bin/ci`). `bin/ci` (worker, full, on the worktree): format 14s, build 18s, test 222s, native 183s, jdtls 364s, all passed. Not reviewed: small ticket, the production change is one constant and its Javadoc in `JdtlsSettings` (a setting sent to jdtls, no input parsed, no I/O); the proof is the replay on the real jdtls.

- Setting: `java.completion.filteredTypes` (`Preferences.JAVA_COMPLETION_FILTERED_TYPES_DEFAULT` holds `com.sun.*`, `io.micrometer.shaded.*`, `java.awt.*`, `jdk.*`, `org.graalvm.*`, `sun.*`, but jdtls pushes the list into the Eclipse `TypeFilter` preference only when the client sends the key, so without it the filter is not applied to the search of organize-imports). `JdtlsSettings` now sends `["com.sun.*", "sun.*", "jdk.*"]`; `java.awt.*` is left out because it is public (`List` stays a real choice between `java.util.List` and `java.awt.List`).
- Reproduced first on the real jdtls without the setting: `Pattern` refused with the candidates `com.sun.org.apache.xalan.internal.xsltc.compiler.Pattern` and `java.util.regex.Pattern`.
- Scenario `organize-imports-of-a-type-a-jdk-internal-class-also-declares` (its own project, one more jdtls start of about 10 s in the replay); `organize-imports-of-a-type-two-packages-declare` (`Date`) is still refused, its candidates are now `java.sql.Date` and `java.util.Date`.
- Limit, in `docs/commands/organize-imports.md`: the filter works by package, so a class of the user's own project under `com.sun.` or `sun.` is never a candidate. Not tested: a file that already imports a filtered type.
- No gabier command applied to this change (scenarios, a constant, docs); no friction.

Tokens:
- worker: 121300 (`subagent_tokens`).
- reviewer: none, not reviewed.
- supervisor: 17 calls, output 14654, input + cache write 29603 (cache read 6194555); covers the supervisor session over the period of the ticket (launch of the worker to now), other work included (the coupling and seed4j questions, three issues opened); exchanges with the user: 2 messages.
