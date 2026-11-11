## Java with gabier

Change Java through a language server with `gabier`, not by editing text. `gabier help` lists the commands.

- At the start of a session, run `gabier start` in the background: jdtls takes about a minute to import the project.
- Before editing Java by hand, ask `gabier help --for="<the task, in English>"`: it says if a command does it.
- Rename, extract, move, inline or create with `gabier rename`, `gabier extract-method`, `gabier move`, `gabier inline`
  and `gabier create`, not by editing every occurrence.
- Try a change with `--dry-run` first: it prints the change and writes nothing.
- See the errors of the files you changed with `gabier diagnostics <file>...`, in one call, not with a build; fix them
  with `gabier actions` and `gabier apply-action`.
- After adding or removing a usage, run `gabier organize-imports <file>`.
- A refusal says why; `gabier help <command>` gives the usage and the other ways. A text edit is the last resort.
- At the end of the session, run `gabier stop`.
