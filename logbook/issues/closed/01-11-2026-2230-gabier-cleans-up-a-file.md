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

## Resolution

Commit 4bfce57. `bin/ci` (worker, full, once): format 9s, build 3s, test 140s, native 154s, jdtls 492s, all passed, none skipped. Reviewed once: nothing blocking.

- Behaviour: the adapter sends `workspace/didChangeConfiguration` with the start preferences plus `java.cleanup.actions=[ids]`, asks `java/cleanup` (a `TextDocumentIdentifier`), then sends the configuration again with `actions=[]` in a `finally`. No client capability is added; `canUseInternalSettings` stays unset (if true, jdtls reads the JDT UI preferences instead), and `java.saveActions.cleanup` stays false. jdtls answers one edit covering the whole file, or an empty list when nothing changes (that is "nothing to change", not an error).
- Unknown ids: jdtls only logs them, so the core refuses them (domain enum `CleanUp`, the 19 short ids of jdtls, key `editing-unknown-clean-up`, user's choice of 01-11-2026 over a refusal in the adapter). `organizeImports` is left out and refused as unknown, pointing to `gabier organize-imports`: the jdtls cleanup would bypass its guards (ambiguous and unresolved imports); reversible with one enum constant.
- Found on the real jdtls: `stringConcatToTextBlock` rewrites a concatenation of three literals or more, not two; it indents the text-block lines 4 columns past `return`.
- The order of several ids is read from the jdtls source (`LinkedHashSet`, each cleanup re-parses the working copy), set in order by the adapter (`JdtlsCleanUpTest`), but no scenario shows it; see the suggestions.
- Suggestions: issue `01-11-2026-2318-cleanup-review-suggestions.md` (on hold).
- Process: the worker stopped once, believing the session was in plan mode, and resumed after a question to the user; it renamed a fixture with `sed`, not `gabier rename`.

Tokens:
- worker: 394737 (`subagent_tokens` of its last notification, which already includes the first one, 207263: the worker was resumed once).
- reviewer: 83519 (`subagent_tokens`).
- supervisor: 15 calls, output 10596, input + cache write 308451 (cache read 2227497); covers the supervisor session over the period of the ticket (launch of the worker to now), other work included; exchanges with the user: 0 messages typed (two questions answered through the question tool).
