# gabier rename answers for the name of a module

Opened: 03-11-2026 14:53

Met on 03-11-2026: `gabier rename --dry-run modules/cli/src/main/java/module-info.java 1 8 eu.gabier.client` (the name in
`module eu.gabier.cli {`) answers `internal-server-error`, with no description. Renaming a module is a text edit
today (the package rename ticket lists it among them); an agent should get a rename or a refusal that says so.

Done when:
- the rename of a module name renames it, and the `requires` of the other `module-info.java` files, or is refused with a
  key that says that the language server does not do it (then `gabier help rename` says it and the text edit stays);
  never `internal-server-error`; observed on the real jdtls first;
- `bin/ci` passes.

## Resolution

Commit dd80304 (pull request 44). `bin/ci` of the worker, full run on the last commit: format passed, build passed, test passed, native passed, jdtls passed, release-install and release-notes skipped (no changed file reaches them); coupling ceilings unchanged (6 / 5 / 11, 162 scenarios). Check of pull request 44 (`bin-ci`): pass.

- Already met by the mapping of `rename-a-record-component`: a rename on the first segment of the name in `module demo.records {` is answered by jdtls with -32603 (a `NullPointerException`, element null at `RenameSupport.create`, observed on the real jdtls) and is refused as `language-server-request-failed`, never `internal-server-error`. The commit adds the scenario `rename-the-name-of-a-module` (Refused, replayed on the real jdtls), `RenameTest.relaysTheFailureOfTheServerOnTheNameOfAModuleAndWritesNothing`, `OperationsTest.tellsThatRenamingTheNameOfAModuleIsATextEdit`, and says in `gabier help rename`, `docs/commands/rename.md` and `docs/improve.md` that the name of a module and the `requires` of the other descriptors stay text edits.
- Interpretation of the `Done when` point "a key that says that the language server does not do it": the key says the request failed; the help says that the rename of a module is a text edit. gabier does not rename modules itself.
- Review: no blocking finding. The suggestions are grouped in `11-11-2026-*-failure-log-and-request-failed-review-suggestions.md` (on hold), except the wording of `docs/daemon.md` on what the log holds (it may hold a path or a name), fixed by the supervisor.

Tokens:
- worker: 116793 (one worker for the batch of four issues, three commits)
- reviewer: 125503 (one review of the batch)
- supervisor: 34 calls, output 22845, input + cache write 241562 (cache reads 4118124), over the period of the batch (launch of the worker 11-11-2026 01:47 UTC to the merge), other work included; exchanges with the user: 0 messages
