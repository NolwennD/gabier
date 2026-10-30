# A probe that starts jdtls and waits for the import

Opened: 30-10-2026 02:46

Write an integration test in the daemon module that starts a real jdtls (the path of the launcher in `GABIER_JDTLS`, a
fresh `-data` directory) on this project with LSP4J, then waits until the import is really done: the server says it is
ready, and it lists the modules of the project. It always stops jdtls and deletes its data directory, even on failure,
and fails loudly, never skips, when `GABIER_JDTLS` is missing. It is a Gradle task `jdtlsProbe`, not part of `check`,
which switches on the `jdtls` stage of `bin/ci`. It is the seed of the future LSP client: keep it clean, do not
generalise it.

Done when:
- `./gradlew jdtlsProbe` ends green on the current project, and the `jdtls` stage of `bin/ci` runs it, not skipped;
- no jdtls process is left running afterwards, and no data directory;
- without `GABIER_JDTLS`, it fails at once with a clear message.

Depends on: daemon-module-with-lsp4j, pin-jdtls-with-mise.
