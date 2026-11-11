Gabier changes Java through the language server of the project. Use its tools instead of editing Java as text.

- Before editing Java by hand, ask `guide` which tool does the change, with the task in English: it names the tool, or
  says that the language server does not offer it. The tools that change code: `rename`, `rename-token`, `move`,
  `inline`, `create`, `cleanup`, `organize-imports`, `extract-variable`, `extract-method`, `extract-constant`,
  `extract-field`, `move-type-to-file`, `apply-action`. A text edit is the last resort.
- A file is given by its absolute path (a relative path is refused: the server does not know the folder you work in), and
  the call acts on the project of that file, the worktree you entered included; the files of one call must be in one
  project. Every line and column is counted from 1, as an editor shows them.
- Try a change that crosses files with dryRun set to true: it answers the change and writes nothing. Call it again with
  dryRun false to write.
- After a change, call `diagnostics` once with all the files that changed, and `organize-imports` after adding or
  removing a usage.
- To fix an error, or to rewrite code, list what the language server offers on a range with `actions`, then apply one by its
  title with `apply-action`. `references` lists where a symbol is used.
- The server starts the daemon when it connects, and jdtls imports the project meanwhile. The first call that asks the
  daemon waits for the import, about a minute for a Gradle project; the server goes on answering meanwhile, and `status`
  answers at once with where jdtls is and what it is importing. `status` starts no daemon: the connection has started
  one already. A call that the daemon does not answer in 11 minutes ends as a failed call (the key daemon-timeout).
- A call that fails answers why (isError): read it before calling again.
