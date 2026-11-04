# Use gabier on gabier

gabier exists so that an agent writes Java through a language server. Since 31-10-2026 an agent working on this
repository does it with `gabier` (installed on the PATH by `mise run install`, which copies it out of the checkout, so a
clean or another branch does not break it; the cli starts the daemon of its own copy by itself):

- `gabier diagnostics <file>` to see the errors of a Java file instead of waiting for a build;
- `gabier rename <file> <line>:<column> <new-name>` (1-based position, or `--token`; `--dry-run` first on a rename that
  crosses files) instead of editing every occurrence by hand; on the name in a `package` line it renames the package,
  its files moved to the folder of the new name (whole new name; one source folder at a time: the same package of the
  tests is a second call; `module-info.java`, string literals and build files stay text edits);
- `gabier organize-imports <file>` after adding or removing a usage, instead of editing the import lines by hand (it follows
  the import preferences of the project, here `.vscode/settings.json`: the order of the formatter and no star import;
  run `gabier diagnostics <file>` first, a file whose imports
  jdtls cannot resolve loses them);
- `gabier cleanup [--dry-run] <file> <cleanup>...` to rewrite a whole file with clean-ups of jdtls, applied in the order
  given, instead of editing the text (for instance `stringConcatToTextBlock` for a concatenation of string literals that
  should be a text block; the identifiers are in `gabier help cleanup`; a file with nothing to clean answers `nothing to
  change`; `organizeImports` is not one of them, use `organize-imports`);
- `gabier extract-variable [--dry-run] <file> <line>:<column> <line>:<column> <name>` to turn an expression into a local
  variable, `gabier extract-method [--dry-run] <file> <line>:<column> <line>:<column> <name>` to turn statements, or an
  expression, into a method, and `gabier extract-constant [--dry-run] <file> <line>:<column> <line>:<column> <name>` to
  turn an expression that reads no local variable and no instance member into a `private static final` constant, and
  `gabier extract-field [--dry-run] <file> <line>:<column> <line>:<column> <name>` to turn an expression into a field of
  the class (1-based positions, end excluded);
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
  listed), instead of searching the text;
- `gabier actions <file> <line>:<column> <line>:<column>` to list what jdtls offers on a range (the fixes of an error, such
  as the import of a type that is missing or a method to create, and the rewrites of code, such as a concatenation to a text
  block; 1-based, end excluded, a point is the same position twice), and `gabier apply-action [--dry-run] <file>
  <line>:<column> <line>:<column> <title>` to apply one by its title (the title is the key, the number of the list changes
  from one call to the next): a fix of what `gabier diagnostics` reports goes through them, not through a text edit. A Java
  refactoring that gabier has a command for goes through it;
- `gabier help --for="<the task, in English>"` **before editing Java by hand**, to learn whether jdtls does the move and
  with which command: it answers one to three sections of the guide (`gabier help guide`) with the command to run and a
  confidence, or says that nothing matches (a task that jdtls does not offer is answered as `By hand:`, with the reason).
  A wrong answer, or an abstention, on a real task is a friction, so a ticket (below), and the fix is usually an entry of
  the guide or the words of its `Words:` line (skill `extend-guide`).

An operation gabier does not have yet is done as a text edit and becomes an issue (`.claude/rules/workflow.md`, step 4 of the
development loop). The daemon serves one project, and there is one socket per project: `gabier` picks the socket of the
project it is run in (`GABIER_PROJECT`, else the nearest folder above the working directory that holds a `.git`, which
is the root of a worktree), `<folder>/<name of the project>-<hash of its path>.sock` in `$XDG_RUNTIME_DIR/gabier`
(`/tmp/gabier-<uid>` when it is not set), so **a worker runs `gabier` in its worktree with no prefix and no variable**:
two worktrees have two daemons, and a command run in one never reaches the other. `GABIER_SOCKET` still replaces the
socket, for a test. The worker ends with `gabier stop`, run in its worktree, before its report; the supervisor stops the
daemon of another worktree with `GABIER_PROJECT=<worktree> gabier stop`. The skills `ticket` and `ship` run
`scripts/stop-stray-daemons.sh`, which stops the daemon of a worktree that no longer exists, for a worker that was
stopped before it could.

The jdtls workspace is temporary (a folder of `/tmp` removed at each stop; a daemon that starts jdtls removes the ones that
killed daemons left). The jars that every jdtls indexes are shared in `~/.cache/gabier` (`GABIER_CACHE` moves it).

**A friction met while using gabier is a ticket**: a wrong answer, a refusal that is not explained, a command that is
missing, a slow answer. The worker reports it to its supervisor with the command, the file and the answer, as for any
unrelated bug (`.claude/rules/logbook.md`). These tickets come before the findings of a review.
