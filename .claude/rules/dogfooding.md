# Use gabier on gabier

gabier exists so that an agent writes Java through a language server. Since 31-10-2026 an agent working on this
repository does it with `gabier` (installed on the PATH by `mise run install`; the cli starts the daemon by itself):

- `gabier diagnostics <file>` to see the errors of a Java file instead of waiting for a build;
- `gabier rename` (position or `--token`, `--dry-run` first on a rename that crosses files) instead of editing every
  occurrence by hand;
- `gabier organize-imports <file>` after adding or removing a usage, instead of editing the import lines by hand (it keeps
  the order of the formatter and never writes a star import; run `gabier diagnostics <file>` first, a file whose imports
  jdtls cannot resolve loses them);
- `gabier extract-variable [--dry-run] <file> <line>:<column> <line>:<column> <name>` to turn an expression into a local
  variable, `gabier extract-method [--dry-run] <file> <line>:<column> <line>:<column> <name>` to turn statements, or an
  expression, into a method, and `gabier extract-constant [--dry-run] <file> <line>:<column> <line>:<column> <name>` to
  turn an expression that reads no local variable and no instance member into a `private static final` constant
  (1-based positions, end excluded);
- `gabier move-type-to-file [--dry-run] <file> <line>:<column>` to make a nested type a top-level type in a file of its
  own (1-based; the innermost type that encloses the position; an inner class that uses its outer instance gets a field and
  a constructor parameter, which a JUnit `@Nested` class cannot take: see its help);
- `gabier move [--dry-run] <file>... <package>` to move Java classes to a package, creating it when it is missing (it
  rewrites the `package` lines, the imports and the references; run `gabier diagnostics` afterwards for the
  package-private members it cannot see);
- `gabier inline [--dry-run] <file> <line>:<column>` to replace a local variable, a method or a constant by its value and
  remove its declaration (1-based; on the declaration of a method or a constant every use is inlined, on one use that use
  only: see its help);
- `gabier create [--dry-run] <file> <class|interface|record|enum>` to create a Java file that holds an empty type, with the
  right `package` line and no comment, instead of writing the file by hand (the folders of a new package are made with it);
- `gabier references [--include-declaration] <file> <line>:<column>` to list where a symbol is used (1-based; a type, a
  method, a field, a local variable or a parameter of the project, tests included; the accessors of a field are not
  listed), instead of searching the text. A Java refactoring that gabier has a command for goes through it.

An operation gabier does not have yet is done as a text edit and becomes an issue (`.claude/rules/workflow.md`, step 4 of the
development loop). The daemon serves one project, and there is one socket per user: two worktrees would share a daemon
and a command run in one would change the files of the other. **In a worktree, a worker gives gabier a socket of its own**
and runs the command in that worktree: prefix every call, the shell does not keep variables from one command to the next,
with `GABIER_SOCKET="$XDG_RUNTIME_DIR/gabier/$(basename "$(git rev-parse --show-toplevel)").sock"` (the folder of the default
socket, `/tmp/gabier-<uid>` when `XDG_RUNTIME_DIR` is not set; a socket path holds 107 bytes at most), `gabier status` and
`gabier stop` included. The worker ends with `gabier stop` on that socket,
before its report. The skills `ticket` and `ship` run `scripts/stop-stray-daemons.sh`, which stops the daemon of a worktree
that no longer exists, for a worker that was stopped before it could.

**A friction met while using gabier is a ticket**: a wrong answer, a refusal that is not explained, a command that is
missing, a slow answer. The worker reports it to its supervisor with the command, the file and the answer, as for any
unrelated bug (`.claude/rules/logbook.md`). These tickets come before the findings of a review.
