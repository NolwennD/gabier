# A missing file under the project is not reported as outside the project

Opened: 30-10-2026 15:04

`JdtlsLanguageServer` turns the `NoSuchFileException` of a file under the project root (for example
`src/main/java/demo/Missing.java`) into `FILE_OUTSIDE_PROJECT`: wrong reason for the user. The test
`refusesAFileThatDoesNotExistAsNotBeingInTheProject` pins that behaviour; it was not required by the issue.

Done when:
- [ ] The language-server error keys say a file does not exist, apart from a file outside the project (the existing keys
      are in `LanguageServerErrorKey`; add one only if none fits), with its message.
- [ ] A test asks the diagnostics of a missing file under the project and gets that key; one asks a file outside the
      project and still gets `FILE_OUTSIDE_PROJECT`.
- [ ] If the contract scenarios change, their replay on the real jdtls still passes (skill `add-scenario`).
- [ ] `bin/ci` green.
