# gabier organizes the imports of a file

Opened: 31-10-2026 16:46

An agent that adds or removes a usage fixes the imports of a file with `gabier organize-imports <file>`, instead of
editing import lines by hand. Same shape as `rename`: a daemon method (skill `add-daemon-method`) and the cli command, with
`--dry-run`; the answer is the files changed and the unified diff, text and `--json`; a refusal is the problem object.

**Care (user's warning)**: organizing imports must not turn explicit imports into `*` and must not reorder what the project
orders already. This is configured, not fought (`.claude/rules/workflow.md`, "Value first"): start by reading the Eclipse
JDT LS documentation, and write in the resolution which settings and capabilities give the behaviour. Expected candidates
to check in the docs, not to assume: `java.sources.organizeImports.starThreshold` and `staticStarThreshold` (so that no
import becomes `*`), `java.completion.importOrder` (the order of the groups), and the `source.organizeImports` code action
declared in the client capabilities, rather than the command `java.edit.organizeImports` and its pushed `applyEdit`. The
settings go in `JdtlsSettings`, the capabilities in `JdtlsCapabilities`; the order matches what the project's Checkstyle and
formatter keep, so that organizing a file of gabier changes nothing when its imports are in order.

Done when:
- [ ] `gabier organize-imports <file>` adds a missing import, removes an unused one and sorts the rest, on disk;
      `--dry-run` writes nothing; shown on the fake language server (`GabierTest`, daemon tests on a real socket) and with a
      contract scenario replayed on the real jdtls (skill `add-scenario`).
- [ ] On the real jdtls, a file with explicit imports (more of one package than the default threshold would collapse, and
      static imports) keeps them explicit: no `*` appears, the order is the project's (a test that fails with the default
      settings, red first).
- [ ] Organizing the imports of existing sources of gabier that are in order changes nothing (a probe or test on a few files).
- [ ] The files changed and the diff are in the answer, in text and `--json`; README and usage text document it with an
      example; refusals are problem objects, nothing written.
- [ ] `bin/ci` green, coverage 100%.

Depends on: 31-10-2026-0825-gabier-renames-a-symbol (closed; same files, the change report and the daemon method shape).
