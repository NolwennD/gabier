# Use gabier on gabier

gabier exists so that an agent writes Java through a language server. Since 31-10-2026 an agent working on this
repository does it with `gabier` (installed on the PATH by `mise run install`; the cli starts the daemon by itself):

- `gabier diagnostics <file>` to see the errors of a Java file instead of waiting for a build;
- `gabier rename` (position or `--token`, `--dry-run` first on a rename that crosses files) instead of editing every
  occurrence by hand. A Java refactoring that gabier has a command for goes through it.

An operation gabier does not have yet is done as a text edit and becomes an issue (`.claude/rules/workflow.md`, step 4 of the
development loop). The daemon serves one project: from a worktree, run the command in that worktree (the daemon of another
checkout keeps its own project: `gabier stop` first).

**A friction met while using gabier is a ticket**: a wrong answer, a refusal that is not explained, a command that is
missing, a slow answer. The worker reports it to its supervisor with the command, the file and the answer, as for any
unrelated bug (`.claude/rules/logbook.md`). These tickets come before the findings of a review.
