# Moving a private nested type to its own file leaves no `private` top-level type

Opened: 26-11-2026 01:51

Met while using gabier on another project (reported by the user; the move was then done by hand). `gabier move-type-to-file`
on a nested **record** declared `private` answered a dry run whose new file declares `private record ...` at top level,
which Java refuses (a top-level type is public or package-private). No refusal, no warning: an agent that applies the answer
writes a file that does not compile.

An agent that moves a private nested type gets a file that compiles, or a refusal that says what to do, never a silent
invalid file. The case is not known to be limited to records: check an enum, an interface and a class too.

Done when:
- The case is reproduced on the real jdtls (observe first, as `.claude/rules/workflow.md` says: a throwaway replay that
  prints the change set), for a private nested record, and for the other kinds of nested type, and the resolution says
  which of them show it.
- A contract scenario of the catalogue holds the case, replayed against the real jdtls (skill `add-scenario`); its
  effect (the files once applied, compiled by the replay) is what is compared.
- The resolution says whose behaviour it is (jdtls's move refactoring, or the way gabier asks for it) and what was done:
  reported upstream, or a setting that gives the good result; a wrong answer of jdtls is relayed and documented, not
  guessed in advance (`.claude/rules/workflow.md`, "jdtls is never fought"). Only a light workaround in the adapter is
  allowed, and the resolution says why.
- `gabier help move-type-to-file` says what the agent sees and runs next for this case (for instance
  `gabier diagnostics` on the new file), in the form of the help rules (`.claude/rules/help.md`).
