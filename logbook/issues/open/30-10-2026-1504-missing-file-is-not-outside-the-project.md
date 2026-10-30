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
- [ ] The language-server error keys say a file does not exist, apart from a file outside the project (the existing keys
      are in `LanguageServerErrorKey`; add one only if none fits), with its message.
- [ ] A test asks the diagnostics of a missing file under the project and gets that key; one asks a file outside the
      project and still gets `FILE_OUTSIDE_PROJECT`.
- [ ] If the contract scenarios change, their replay on the real jdtls still passes (skill `add-scenario`).
- [ ] `bin/ci` green.
- [ ] A directory under the project is refused with a problem object that says it is not a file (a key of its own
      or the one for a missing file, stated), not an internal error; a test shows it.
