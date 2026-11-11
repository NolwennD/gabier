# gabier create makes a package-info.java

Opened: 04-11-2026 09:35

An agent that adds a package creates its `package-info.java` (the project rule: every package with classes has one,
`@NullMarked`) with `gabier create`, not by hand. Met on 04-11-2026 by the worker of
`gabier-help-answers-a-question-about-a-task`: `gabier create <file> <class|interface|record|enum>` has no form for
`package-info.java`, so the file of the new package `eu.gabier.cli.ranking` was written by hand.

Done when:
- [ ] `gabier create [--dry-run] <dir>/package-info.java package` writes the file with the `package` line and the
  `@org.jspecify.annotations.NullMarked` annotation the architecture test demands, and nothing else; `gabier help
  create`, `docs/commands/create.md` and the refusals agree; a scenario replayed on the real jdtls or the reason why
  not;
- [ ] `bin/ci` passes.

## Resolution

Commit 5251d32 (pull request 42, merged fast-forward). `bin/ci` of the worker, full run on the last commit: format passed, build passed, test passed (cli 1554, core 727, daemon 1022, protocol 414, shared 236, architecture 22; coverage 100%), release-install and release-notes skipped (no changed file reaches them), native passed, jdtls passed; coupling ceilings unchanged (159 scenarios). Check of pull request 42 (`bin-ci`): pass.

- `gabier create <dir>/package-info.java package` writes `@org.jspecify.annotations.NullMarked` and the `package` line, nothing else; refusals for a file not named `package-info.java`, an existing file, the default package, a folder outside the source folders, a folder that is not an identifier, outside the project and not ready (`CreatePackageInfoTest`, `CreateTypeMethodTest`, `JdtlsLanguageServerTest`); seven scenarios replayed on the real jdtls (`ScenarioReplayTest`).
- A light workaround, in the secondary adapter: jdtls offers no snippet for `package-info.java` (observed on the real jdtls with a throwaway probe: the completion at 0:0 of an empty file gives only the keyword `package`), so `JdtlsLanguageServer.createPackageInfo` writes the text itself, the package taken from the folder under the innermost source folder (`java.project.listSourcePaths`, as `createType` does). The annotation is jspecify's, said in `docs/commands/create.md`.
- Review: no blocking finding. One suggestion, a Javadoc misplaced in `Scenarios.java` (on `withNullMarked()` instead of `withProblems()`), is a typo fix made by the supervisor.

Tokens:
- worker: 315858 (one worker for the batch of three issues, one commit each)
- reviewer: 145126 (one review of the batch)
- supervisor: 30 calls, output 13576, input + cache write 164895 (cache reads 2684070), over the period of the batch (launch of the worker 10-11-2026 23:46 UTC to the merge), other work included; exchanges with the user: 4 messages
