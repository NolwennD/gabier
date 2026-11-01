# gabier applies jdtls cleanups to a file

Opened: 01-11-2026 22:30

An agent rewrites a whole Java file with a jdtls cleanup, for instance `stringConcatToTextBlock`, with
`gabier cleanup [--dry-run] <file> <id>...` instead of editing text (decision of the user, 01-11-2026: a command that
takes cleanup identifiers, `stringConcatToTextBlock` the first one). The answer is the files changed and the unified
diff, text and `--json`; a refusal is a problem object, nothing written. Daemon method (skill `add-daemon-method`), cli
command, `docs/commands/cleanup.md`, a line in `help` and in `.claude/rules/dogfooding.md`.

**Care**: jdtls is configured, not fought. Start with `ripwire` and the JDT LS source at the tag of `mise.toml`
(`.claude/rules/workflow.md`, "Orientation"). Lead: the request `java/cleanup` takes a `TextDocumentIdentifier` and runs the
identifiers of the setting `java.cleanup.actions` (`SaveActionHandler.performManualCleanupActions`, `CleanUpRegistry`), so
the adapter sets that setting to the identifiers asked for before the request; read the documentation of the setting and
say in the resolution which settings and capabilities give the behaviour. An unknown identifier is refused with the
identifiers jdtls knows (`CleanUpRegistry`), nothing written. Run against the fake language server first, then a contract
scenario replayed on the real jdtls (skill `add-scenario`) with `stringConcatToTextBlock` on a file that holds a
concatenation with an escaped quote.

Done when:
- [ ] `gabier cleanup <file> stringConcatToTextBlock` rewrites the concatenations of the file into text blocks, on disk;
      `--dry-run` writes nothing; shown on the fake language server (`GabierTest`, daemon tests on a real socket) and with
      a contract scenario replayed on the real jdtls.
- [ ] Several identifiers in one call apply in the order given; an unknown identifier, a file outside the project and a file
      with nothing to clean are each answered as such (a file with nothing to clean is not an error), nothing written on a
      refusal.
- [ ] `help`, the command's document and the dogfooding rule list the command; `bin/ci` passes.
