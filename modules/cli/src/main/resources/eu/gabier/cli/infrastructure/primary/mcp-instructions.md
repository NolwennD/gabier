Gabier changes Java through the language server of the project. Use its tools instead of editing Java as text.

- Before editing Java by hand, ask `guide` which tool does the change, with the task in English: it names the tool, or
  says that the language server does not offer it. The tools that change code: `rename`, `move`, `inline`, `create`,
  `cleanup`, `organize-imports`, `extract-variable`, `extract-method`, `extract-constant`, `extract-field`,
  `move-type-to-file`. A text edit is the last resort.
- A file is given by its path; every line and column is counted from 1, as an editor shows them.
- Try a change that crosses files with dryRun set to true: it answers the change and writes nothing. Call it again with
  dryRun false to write.
- After a change, call `diagnostics` on the files that changed, and `organize-imports` after adding or removing a usage.
- To fix an error, or to rewrite code, list what the language server offers on a range with `actions`, then apply one by its
  title with `apply-action`. `references` lists where a symbol is used.
- The first call waits while jdtls imports the project, about a minute for a Gradle project; `status` says where it is.
- A call that fails answers why (isError): read it before calling again.
