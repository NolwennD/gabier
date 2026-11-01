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
- [ ] A class, an interface, a record and an enum are created in a new file, in the package derived from the path (a file in the default
      package gets no `package` line), with no comment (no header, no Javadoc); `--dry-run` writes nothing. Shown on the fake and by one
      scenario per kind replayed on the real jdtls.
- [ ] Refusals, each a scenario replayed on the real jdtls or a refusal of gabier with its reason in the resolution: the file exists
      (nothing overwritten), a file name that is not a Java type name, a file outside the project, a kind that is not one of the four.
- [ ] The settings that remove the comments are in `JdtlsSettings`, with a test that fails if the generated file has a comment; the same
      settings apply to the other commands that generate code (`move-type-to-file` wrote an empty Javadoc on a generated field: say if they
      remove it, and if not, why).
- [ ] `docs/commands/create.md`, the line of the README list, the row of `docs/protocol.md`, the help of the command (with its refusals)
      are added; `.claude/rules/dogfooding.md` names the command. `bin/ci` green, coverage 100%.
