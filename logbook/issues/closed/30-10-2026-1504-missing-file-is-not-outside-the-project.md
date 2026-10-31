# A missing file under the project is not reported as outside the project

Opened: 30-10-2026 15:04

`JdtlsLanguageServer` turns the `NoSuchFileException` of a file under the project root (for example
`src/main/java/demo/Missing.java`) into `FILE_OUTSIDE_PROJECT`: wrong reason for the user. The test
`refusesAFileThatDoesNotExistAsNotBeingInTheProject` pins that behaviour; it was not required by the issue.

Found since, by the review of `the-daemon-answers-diagnostics-of-a-file`: a directory inside the project
(`gabier diagnostics src`) makes `readString` throw an `IOException` ("Is a directory", `JdtlsLanguageServer.java:230-234`),
which becomes an `UncheckedIOException`: the client gets an internal error, not a refusal. The fake cannot show it: it
answers a path it was not scripted for with an `AssertionError`.

Done when:
- [x] The language-server error keys say a file does not exist, apart from a file outside the project (the existing keys
      are in `LanguageServerErrorKey`; add one only if none fits), with its message.
- [x] A test asks the diagnostics of a missing file under the project and gets that key; one asks a file outside the
      project and still gets `FILE_OUTSIDE_PROJECT`.
- [x] If the contract scenarios change, their replay on the real jdtls still passes (skill `add-scenario`).
- [x] `bin/ci` green.
- [x] A directory under the project is refused with a problem object that says it is not a file (a key of its own
      or the one for a missing file, stated), not an internal error; a test shows it.

## Resolution

Merged as `abc9423` (first commit of the daemon batch, with the two issues below; one worker, one `bin/ci`, one review). A
file that does not exist under the project is refused as `language-server-file-missing` and a folder as
`language-server-not-a-file`; a file outside the project stays `language-server-file-outside-project`; an unreadable file
stays an internal error. `JdtlsLanguageServer.textInProject` checks "outside the project", then "is a folder", then reads.

Behaviour change on purpose: the test that pinned the wrong key (`refusesAFileThatDoesNotExistAsNotBeingInTheProject`) is
replaced by `refusesAFileThatDoesNotExistAsMissing`. Proofs: `JdtlsLanguageServerTest$Refusing` (missing file, folder, outside
the project, `failsWhenTheFileCannotBeReadAtAll` with a `chmod 000` file), `DiagnoseTest` (missing, folder),
`LanguageServerExceptionTest`, `ScenarioTest`, `ScenariosTest`, and the scenarios `diagnostics-of-a-file-that-does-not-exist`
and `diagnostics-of-a-folder` replayed on the real jdtls. `docs/commands/diagnostics.md` updated.

Worker's `bin/ci` for the whole batch (on the tree that became the third commit; the second commit was then amended for a
`.md` only and the third cherry-picked over it, code unchanged): format 7s, build 9s, test 128s, native 137s, jdtls 378s; no
stage skipped; cli 140, core 333, daemon 502, architecture 19, 0 failures; coverage 100%, Checkstyle green. Red first: tests
and code of this ticket went in together before the first run (at most a compile red).

Review (neutral reviewer, once, for the batch): nothing blocking. Suggestion for this ticket: a path through a file
(`Main.java/x`) gives an internal error, not a refusal, and the test of the unreadable file fails as root: issue
`31-10-2026-2243-daemon-batch-review-suggestions.md` (on hold).

Tokens (the batch of three issues, counted once, here):
- worker: 335972
- reviewer: 117097
- supervisor: 26 calls, 19615 output, 36609 input + cache write (cache reads 5131713), exchanges with the user: 4 messages.
  Measured from the launch of the worker to the merge; it includes the talk about jdtls instances, the next tickets and the
  clone of eclipse.jdt.ls.
