# gabier creates a class, an interface, a record or an enum

Opened: 01-11-2026 13:33

An agent creates a Java type with `gabier create` instead of writing a file by hand: `gabier create [--dry-run] <file> <class|interface|record|enum>`.
The file is created with the right `package` line, derived from its path, and an empty type of the kind asked, named after the file.
The answer is the files created and the unified diff, text and `--json`; a refusal is a problem object, nothing written. Daemon
method (skill `add-daemon-method`) and cli command on the shape of `move-type-to-file`.

**Care**: jdtls is configured, not fought. The behaviour reference is the `create` of the Python driver of chatbot (`../chatbot/.claude/tools/jdtls-driver.py`;
design: `docs/superpowers/specs/2026-10-29-gabier-design.md`, "create"): it opens an empty file and takes jdtls's completion snippet, which
derives the package from the path; `enum` has no snippet in that jdtls version and is a rewritten `class` one. The worker starts from the Eclipse
JDT LS documentation and source (clone at /home/nolwenn/dev/projects/eclipse.jdt.ls, read-only) and writes in the resolution which settings give
the behaviour. jdtls generates comments by default (a file header, a Javadoc on the type): the generated file has **none**, and the settings
that turn them off (the `java.templates.*` and `java.codeGeneration.*` families, to be found in the documentation and set in
`JdtlsSettings`) are part of the work. Tests first, fake first, then scenarios replayed on the real jdtls (skill `add-scenario`).

Done when:
- [x] A class, an interface, a record and an enum are created in a new file, in the package derived from the path (a file in the default
      package gets no `package` line), with no comment (no header, no Javadoc); `--dry-run` writes nothing. Shown on the fake and by one
      scenario per kind replayed on the real jdtls.
- [x] Refusals, each a scenario replayed on the real jdtls or a refusal of gabier with its reason in the resolution: the file exists
      (nothing overwritten), a file name that is not a Java type name, a file outside the project, a kind that is not one of the four.
- [x] The settings that remove the comments are in `JdtlsSettings`, with a test that fails if the generated file has a comment; the same
      settings apply to the other commands that generate code (`move-type-to-file` wrote an empty Javadoc on a generated field: say if they
      remove it, and if not, why).
- [x] `docs/commands/create.md`, the line of the README list, the row of `docs/protocol.md`, the help of the command (with its refusals)
      are added; `.claude/rules/dogfooding.md` names the command. `bin/ci` green, coverage 100%.

## Resolution

Commit c036e93. `gabier create [--dry-run] <file> <class|interface|record|enum>`: core `TypeKind`, `TypeName`, `CreateType`, port method
`JavaLanguageServer.createType`, keys `editing-not-a-type-name` and `language-server-not-creatable`; daemon `JdtlsLanguageServer.createType`,
`NewTypeText`, `CreateTypeMethod`; cli `Create`; 10 contract scenarios (all replayed on the real jdtls, the applied change set compiled);
native check `createWithTheFakeServer`. Docs: `docs/commands/create.md`, README, `docs/protocol.md`, `docs/daemon.md`, `dogfooding.md`.

jdtls is configured, not fought (jdtls 1.63.0): `textDocument/didOpen` of the file with empty text (nothing written; `didClose` in a
`finally`), then `textDocument/completion` at 0:0; jdtls answers snippets labelled class, interface, record, enum
(`SnippetCompletionProposal`), the snippet of the kind is the text of the file, with the `package` line derived from the path. Capability:
completion `snippetSupport`, already declared. **Correction to this issue: jdtls 1.63 has an `enum` snippet**, so the four kinds take
the same path (the Python driver rewrote the `class` one for an older jdtls). Comments: `java.templates.typeComment` is a Javadoc with
the type name by default and an empty value means the default, so the setting is one blank, `[" "]`, set in `JdtlsSettings.javaPreferences`
(`JdtlsSettingsTest` holds the map; the replay compares the text exactly and fails on a Javadoc); `java.templates.fileHeader` is empty by
default; `java.codeGeneration.generateComments` does not govern these snippets. The same setting does **not** remove the empty Javadoc
that `move-type-to-file` writes on a generated field and constructor: that comes from the refactoring's own code templates, which no
`java.templates.*` setting reaches (the replay of `MOVE_AN_INNER_TYPE_THAT_USES_THE_OUTER_INSTANCE` still passes with it); said in
`docs/commands/create.md`. For a file under no source folder jdtls writes no `package` line, silently: the adapter asks
`workspace/executeCommand java.project.listSourcePaths` and refuses with `language-server-not-creatable` (match by path segments).
A record needs Java 14 or later; no snippet for the kind is the same refusal. The cursor `${0}` of the snippet is removed by `NewTypeText`.

Order of work (worker's report): a throwaway probe of the real jdtls first (deleted before the commit); then the core tests and types,
red first and driven by the compiler; the daemon tests against `FakeJdtls`, then the adapter; then the replay on the real jdtls. **Exception:
the cli `Create` capability and its docs were written before their cli tests**, then run green: not strict red-first for the cli.
`gabier create` could not be used for the new classes (no installed build had it); they were written with the Write tool.

Checked: worker's full `bin/ci` green on c036e93 (format 14s, build 5s, test 191s with coverage 100% and Checkstyle, native 193s, jdtls 486s;
cli 399, core 459, daemon 615, protocol 326, shared 236, architecture 19). Review (neutral, once): no blocking finding. Suggestions, opened
as a ticket on hold: no test for a dotted name (`Foo.Bar.java`, `Foo.java.java`); `TypeName` accepts an ignorable character (`Foo` + U+200B)
that `Character.isJavaIdentifierPart` lets through; `NewTypeText` handles a snippet with `\n` only, not `\r\n`; the setting
`[" "]` is proven only by the replay of the `jdtls` stage; `keepsTheInterruptionOfTheThreadThatWaits` does not assert that the file is
closed. Opened as a ticket: a change set creates files, never folders, so `create` in a new package folder is refused with
`editing-folder-missing` and needs a `mkdir -p` first (documented in the page and the help).

Tokens:
- worker: 263144 (subagent_tokens)
- reviewer: 79679 (subagent_tokens)
- supervisor: 15 calls, 6221 output, 34860 input + cache write (3074698 cache read) over the supervisor session from the launch of the
  worker (13:52 UTC) to the close, other work included; exchanges with the user: 0 messages
