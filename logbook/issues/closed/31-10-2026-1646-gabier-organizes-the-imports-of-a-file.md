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
- [x] `gabier organize-imports <file>` adds a missing import, removes an unused one and sorts the rest, on disk;
      `--dry-run` writes nothing; shown on the fake language server (`GabierTest`, daemon tests on a real socket) and with a
      contract scenario replayed on the real jdtls (skill `add-scenario`).
- [x] On the real jdtls, a file with explicit imports (more of one package than the default threshold would collapse, and
      static imports) keeps them explicit: no `*` appears, the order is the project's (a test that fails with the default
      settings, red first).
- [x] Organizing the imports of existing sources of gabier that are in order changes nothing (a probe or test on a few files).
- [x] The files changed and the diff are in the answer, in text and `--json`; README and usage text document it with an
      example; refusals are problem objects, nothing written.
- [x] `bin/ci` green, coverage 100%.

Depends on: 31-10-2026-0825-gabier-renames-a-symbol (closed; same files, the change report and the daemon method shape).

## Resolution

Merged as `1ba953b`. An agent fixes the imports of a file with `gabier organize-imports <file>` (`--dry-run`, text and
`--json`, refusals as problem objects): missing imports added, unused ones removed, the rest sorted, statics first, and
never a `*`.

How jdtls is configured (Eclipse JDT LS documentation and source: vscode-java README, `Preferences.java`, the wiki page on
running jdtls from the command line, `OrganizeImportsHandler.java`), no workaround:
- `JdtlsSettings.javaPreferences()`, sent as `initializationOptions.settings` in `JdtlsSession.awaitReady`:
  `java.sources.organizeImports.starThreshold` and `staticStarThreshold` = `Integer.MAX_VALUE` (the default 99 is not
  "never": 100 types of one package collapse to `*`), and `java.completion.importOrder` = `["#", ""]` (statics, then one
  block). The documentation does not describe that format: it was checked on the real jdtls, and it writes what the
  formatter and Checkstyle (`AvoidStarImport`) keep. The default order `java, javax, org, com` put blank lines between
  groups.
- No new capability: `source.organizeImports`, `resolveSupport` and `dataSupport` were already declared. The adapter asks
  for the `source.organizeImports` code action on the file and resolves it (`codeAction/resolve`); it does not use the
  command `java.edit.organizeImports`. jdtls answers the edit as `changes`, not `documentChanges`, so `RenameChanges` became
  `WorkspaceEdits` and reads both: the former test that refused `changes` was replaced by five that accept it, a behaviour
  change on purpose. Tidy renames made first: `RenameRequest` to `ChangeRequest`.

Proofs (worker's full `bin/ci`: format 9s, build 15s, test 138s, native 19s after a fix of two checks of the ticket itself,
jdtls 385s; no stage skipped; rebased over a change of `scripts/install.sh` only, which no gate reaches, no new `bin/ci`):
- Missing, unused, unsorted on disk, dry run writes nothing: `OrganizeImportsTest` (core), `OrganizeImportsMethodTest`,
  `DaemonServerTest` (real socket), `GabierTest$OrganizingImports`, the native stage, and the scenarios
  `organize-imports-of-a-file`, `-in-order`, `-keeps-explicit-imports`, `-while-the-server-imports`,
  `-of-a-file-outside-the-project` replayed on the real jdtls (the edit applied to a copy and compiled with javac).
- Explicit imports stay explicit, red first: with the initialization options back to `Map.of()`, `ScenarioReplayTest` failed
  (blank lines between groups; `import demo.many.*;` and `import static demo.many.Constants.*;` for 100 types and 100
  constants). Only that replay guards it: the fake-based `OrganizeImportsTest.keepsTheExplicitImports...` and
  `JdtlsSettingsTest` cannot fail on the settings, by design.
- Gabier's own sources in order change nothing: `JdtlsProbeTest.checkImportsOfTheProjectAreInOrder`, 203 sources, 0 would
  change, with two controls that must get the edit; the `fakeServed` source set is excluded (see below).

Review (one pass, no blocking finding). Findings:
- `organize-imports-deletes-what-jdtls-cannot-resolve` (worker's finding, opened): on a file whose imports jdtls cannot
  resolve, it answers an edit that deletes them; a type declared in two packages is left unimported (`chooseImports` is not
  answered).
- `organize-imports-scenarios-and-code-action-choice` (reviewer's suggestions, opened on hold): scenarios for a file with no
  import and for a file whose only import is unused, and the choice of the code action by its kind.
- Friction: `gabier` was not installed when the worker ran, so its renames were text edits plus `git mv`; the
  `OperatorPrecedence` warning at `Main.java:74` is known.

Tokens:
- worker: 389606 (one notification)
- reviewer: 85407
- supervisor: 35 calls, output 13512, input + cache_write 37418 (cache read 4460370), over the period of the ticket, the
  daemon-on-demand closing and the install talk included; exchanges with the user: 4 messages
