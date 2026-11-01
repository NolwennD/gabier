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
- [ ] The references of a type, a method, a field and a local variable are listed with their file, line, column and source line, in the order of
      the files then of the positions; the declaration only with `--include-declaration`. Shown on the fake and by one contract scenario per kind
      replayed on the real jdtls (skill `add-scenario`), including a reference in a test source and one in another package.
- [ ] A symbol with no reference gives an empty list and exit code 0, said in the text answer; `--json` gives `[]`-shaped data of the same object.
- [ ] Refusals, each a scenario replayed on the real jdtls: a position on nothing (blank, comment), a file outside the project, a server that is
      not ready; terminal escapes in a source line are neutralised as in `diagnostics`.
- [ ] `docs/commands/references.md`, the line of the README list, the row of `docs/protocol.md` and the help of the command (with its refusals)
      are added; `.claude/rules/dogfooding.md` names the command. `bin/ci` green, coverage 100%.
