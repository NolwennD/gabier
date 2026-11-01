# A second language server measures how much gabier is coupled to jdtls

Opened: 01-11-2026 10:38
On hold: for memory, decision of the user (01-11-2026): nothing to do now; the user lifts the hold when a second Java language server is worth trying.

The core is written against the port `JavaLanguageServer`, scripted by contract scenarios that are replayed on jdtls. Whether the port
and the scenarios are really independent of jdtls, or carry its habits (the `java/move` request, the shape of its `WorkspaceEdit`,
the refusal messages), is not known: the adapter was written after probing jdtls (ticket
`01-11-2026-0855-gabier-moves-a-nested-type-to-its-own-file.md`). An adapter for another Java language server would show it.

Done when:
- [ ] A second adapter of `JavaLanguageServer` for another Java language server replays the existing contract scenarios.
- [ ] The resolution lists every scenario, port method or core type that had to change, and each reason: the measure of the coupling.
