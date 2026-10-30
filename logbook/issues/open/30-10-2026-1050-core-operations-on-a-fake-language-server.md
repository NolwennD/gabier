# The core diagnoses and renames against a fake language server

Opened: 30-10-2026 10:50

The business of gabier must advance without any real language server (`.claude/rules/workflow.md`, "Fake it until you
run it"). Define the port `JavaLanguageServer` in the domain of the core, with no LSP type: the diagnostics of a source
file (severity, line, column, message) and the rename of the symbol at a position, answered as the change set of
`apply-edits-to-files-atomically`, plus the refusals a server gives (not ready, no such symbol, invalid name, file
outside the project) as typed errors: exceptions of the shared error package, each with its `ErrorKey` and its
parameters (the file, the name, the position), so that one mapper at the edge can later turn any of them into a problem
report. Write a hand-written fake of the port that a test scripts with what the server
would answer, and the application use cases on top of the port: diagnostics of a file, and rename by position or by the
first occurrence of an identifier in a file (the `--token` of the Python driver), applying the answer, or giving the
diff on a dry run. Everything is proven with the fake: no jdtls, no process.

Done when:
- the port, the domain types and the use cases are in the core, null-safe and validated with `Assert`, with the
  architecture tests green and no LSP4J anywhere near them;
- the fake is usable from the tests of other modules (a test fixture of the core), and the tests of the use cases show:
  files written on a rename, nothing written on a dry run or on a refusal, and each refusal reported with its message;
- each answer the fake gives comes from a contract scenario (project files, request, answer in domain types) defined
  once in the test fixtures, in a form the adapters' integration tests can replay against a real server;
- the coverage stays at 100% and nothing in `check` starts a process.

Depends on: apply-edits-to-files-atomically.
