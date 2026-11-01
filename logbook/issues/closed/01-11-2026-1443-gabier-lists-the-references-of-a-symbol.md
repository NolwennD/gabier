# gabier lists the references of a symbol

Opened: 01-11-2026 14:43
Depends on: 01-11-2026-1333-gabier-creates-a-class-an-interface-a-record-or-an-enum.md (decision of the user, 01-11-2026: after `create`; same files:
the command and its capability, the docs and the protocol table).

An agent finds where a type, a method, a field or a variable is used with `gabier references [--json] <file> <line>:<column>` (1-based; the
symbol under the position) before it renames, inlines or moves it, instead of grepping text that matches comments and homonyms. It is the first
read-only command that answers locations, not changed files: the answer is a list of `file:line:column` with the line of source, grouped by file,
the declaration listed apart or not at all (`--include-declaration`, off by default), text and `--json`; nothing is ever written. A refusal is a
problem object. Daemon method (skill `add-daemon-method`) and cli command on the shape of `diagnostics`, which also lists positions.

**Care**: jdtls is configured, not fought. Request `textDocument/references` (`context.includeDeclaration`); read the Eclipse JDT LS documentation
and source (clone at /home/nolwenn/dev/projects/eclipse.jdt.ls, read-only) for what it returns (test sources, the references in other
modules, the dependencies' sources) and which `java.*` settings change it (`java.references.includeAccessors`, `java.references.includeDecompiledSources`),
and write in the resolution which ones are set in `JdtlsSettings`. A reference found in a file not on disk (a jar) is listed or refused: say which.

Done when:
- [x] The references of a type, a method, a field and a local variable are listed with their file, line, column and source line, in the order of
      the files then of the positions; the declaration only with `--include-declaration`. Shown on the fake and by one contract scenario per kind
      replayed on the real jdtls (skill `add-scenario`), including a reference in a test source and one in another package.
- [x] A symbol with no reference gives an empty list and exit code 0, said in the text answer; `--json` gives `[]`-shaped data of the same object.
- [x] Refusals, each a scenario replayed on the real jdtls: a position on nothing (blank, comment), a file outside the project, a server that is
      not ready; terminal escapes in a source line are neutralised as in `diagnostics`.
- [x] `docs/commands/references.md`, the line of the README list, the row of `docs/protocol.md` and the help of the command (with its refusals)
      are added; `.claude/rules/dogfooding.md` names the command. `bin/ci` green, coverage 100%.

## Resolution

Commit c913b83. `gabier [--json] references [--include-declaration] <file> <line>:<column>`, read-only: core `Location`, `ReferenceLocations`,
`Reference`, `ReferenceReader`, `FindReferences` and the port method; protocol `FoundReferences`; daemon method `references`
(`file`, `line`, `column` from 0, `includeDeclaration`); cli `References`; nine contract scenarios replayed on the real jdtls (a type,
a method with its declaration, a field without its accessors, a local variable, a symbol nothing uses, blank, comment, file outside
the project, server importing); the shared replay project gained `src/test/java` (a test source) and `demo.app.Bank` (another package).
Text: the references grouped by file with the line of source, terminal escapes neutralised (`TerminalText`); `--json`:
`{"declarations":[...],"references":[...]}`, exact; no reference: "no references", exit 0. `--dry-run` is a usage error
(`Arguments.without`).

jdtls is configured, not fought: `java.references.includeAccessors=false` (default true lists getter and setter calls as references of
a field) and `java.references.includeDecompiledSources=false` in `JdtlsSettings.javaPreferences`; `java.search.scope` left at `all`, so test
sources are searched (the replay project marks `src/test/java` as `test=true` in `.classpath`); `classFileContentsSupport` is not declared,
so jdtls never answers a class-file or jar location, and the adapter drops non-`file:` URIs (not listed, not refused). `ReferencesHandler`
falls back to the enclosing member when nothing is selected, a wrong answer for a blank or a comment: the adapter asks
`textDocument/prepareRename` first, as `rename` does, and maps a decline to `language-server-no-such-symbol` (no new key). jdtls does not
mark the declaration: with `--include-declaration` the adapter asks twice (false, then true) and takes the difference. Limit, documented:
`prepareRename` also declines the symbols of the JDK and of libraries, a package and a generated method; their references are refused.

Order of work (worker's report): orientation, a throwaway probe on the real jdtls (deleted), then tests with types driven by the compiler:
core domain, `FindReferences`, fixtures and scenarios, protocol, daemon (adapter tests with `FakeJdtls`, settings), replay support, then the cli
tests first (seen red: missing `Arguments.without`, then the unknown command, then the missing page), then the cli code, then the docs and the
native check. **Deviation from strict TDD**: in the core, the fixtures and scenario tests and their code were written before a first run;
compile-red was seen only for `ReferenceLocationsTest`, `ReferenceReaderTest`, `FindReferencesTest` and `FoundReferencesTest`; the expected positions of the
nine scenarios were computed by hand and matched at the first replay. New classes were created with `gabier create`.

Checked: worker's full `bin/ci` green on c913b83 (test with coverage 100%, Checkstyle, architecture and `:daemon:selfCheck`; jdtls 382s); the
`native` stage failed once on the worker's own check in `bin/ci.d/40-native` (stdout of the cli is stripped), fixed and run again alone with the
`jdtls` stage, both green, no Java code changed after the green `test` stage. Review (neutral, once): no blocking finding; the widening of
`ScenariosTest.holdsOnlyRelativeJavaSourcesAndRelativeRequests` (`src/(main|test)/java/.*\.java`) is justified by the new test source and keeps
the other checks. Fixed by the supervisor (`bin/ci --fast` green): the page now lists the package and the generated method among the refusals;
a 150-column Javadoc of `ChangeRequest`. Left, opened as a ticket on hold: the declaration by difference is replayed only on a method (not on a
type, a field, a local variable) and may return several declarations for an overriding method (the page says "the declaration"); a column just
after a name (the `(` of `ledger.deposit(`) passes `prepareRename` but not `references`; the refusals of a JDK type, a keyword and a literal have no
replay; no dedup of repeated locations (jdtls producing one is not shown).

Tokens:
- worker: 188583 (subagent_tokens)
- reviewer: 89794 (subagent_tokens)
- supervisor: 10 calls, 8201 output, 27654 input + cache write (2339218 cache read) over the supervisor session from the launch of the
  worker (14:58 UTC) to the close, other work included; exchanges with the user: 1 messages
