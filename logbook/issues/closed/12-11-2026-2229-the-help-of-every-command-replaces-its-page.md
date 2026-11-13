# The help of every command replaces its page

Opened: 12-11-2026 22:29
Depends on: 12-11-2026-2229-the-help-is-the-documentation-of-a-command (the rule, and `move` as the model).

An agent finds everything it needs about any command in `gabier help <command>`, and `docs/commands/` holds no page of a
command. The commands other than `move` get what their pages said and the help does not, as the rule of the previous ticket
says (the words of the page are not copied: the facts are re-stated, short).

Done when:
- `docs/commands/` holds only what is not a command (`mcp.md`, and the other pages that describe no command, which the worker
  names in the resolution); the page of each command is removed, one commit per group of commands that share a module or a
  family (the batch rule of `.claude/rules/workflow.md`), and the help of each group is read in full.
- Each help states, once and in short sentences, its usage, its output, its refusals and its limits; none names a request, a
  handler, a setting or a class of the language server.
- `CommandRefusalsTest` and anything else that reads a page of a command are removed or point at the code; the README, the
  skills, the guide (`docs/improve.md`) and the other pages link to no removed page.
- `bin/ci` is green, the native image of the cli stays built (the help is a resource of it).

## Resolution

Pull request 57, four commits, fast-forward on `main`: `807996a` (the commands that change code: `rename`,
`organize-imports`, `cleanup`, `extract-variable`, `extract-method`, `extract-constant`, `extract-field`,
`move-type-to-file`, `inline`, `create`), `cac3bcf` (the commands that read the project: `diagnostics`, `references`,
`actions`, `apply-action`, `affected-tests`), `5cd726a` (the commands of the cli: `help`, `init-agent`, `start`,
`status`, `stop`) and `1ed5769`, a Checkstyle order of declarations in `Command` that the first run of the pull request
showed.

- `docs/commands/` holds `mcp.md` only: the MCP server is read by a host, not by an agent through a command help, and
  `McpPageTest` keeps its refusals true. The other pages that describe no command (`cli.md`, `daemon.md`, `protocol.md`,
  `improve.md`) are not in `docs/commands/` and stay.
- What every command of a kind says the same way is written once, in `Command.notes()`: how a position is typed (for a
  command that takes one) and what the answer of a command that changes files looks like (`--dry-run`, the diff, the
  escapes, `--json`), for the commands that have a `dryRun`. `Operations.MOVE` lost its own copy. The notes of an operation
  stay free of the command line (`ToolsTest`: the MCP tools share them).
- Each command states what its page said and its help did not: the settings and the warning of `organize-imports`, the
  four extractions' own limits, the creation, the inlining, `--token`, the output and the path rule of `diagnostics`, the
  lines of `references`, the filter file of `affected-tests`, `--wait` of `start`, the import settings of `status`, the
  flag, the guide and the `--for` rules of `help`. What only explained the adapter (the requests, the capabilities, how
  a name is applied, how the folders are made, how the answer is ordered) is dropped, as `.claude/rules/help.md` says.
- `CommandRefusalsTest` and `CleanupPageLinksTest` are removed with the pages: the keys of the helps are checked against
  the code by `RefusalKeysTest`, and `CleanupHelpTest` keeps the one fact of the second that was not about a link (the
  help of `cleanup` says how to change one place only). `HelpTest` and `ToolsTest` assumed that some command had no notes;
  they now name a case that has none (`ToolsTest`) or expect the notes (`HelpTest`). The rule `help.md`, `AGENTS.md`
  (the release notes read the helps, not `docs/commands`) and the README, `docs/cli.md`, `docs/daemon.md` and
  `docs/protocol.md` no longer link to a removed page; the open issues that named a page name `gabier help <command>`.
- Not done: the notes of the operations that stream (`watch`, `watch-all`, `watch-affected-tests`) still describe the
  notifications of the daemon, which only a client of the daemon reads; they are in the help of `diagnostics` and
  `affected-tests`. A ticket to move them to the protocol page would be tidying.

Checked: the check `bin-ci` of the pull request, green on the last commit (the first run failed on the Checkstyle order
and was fixed). `bin/ci` was not run locally, at the user's request (the machine was loaded): the light tests
(`:cli:test` on the helps, the operations, the tools, the flag and the status/start tests, `:architecture:test`,
`:cli:checkstyleMain` and `checkstyleTest`) were run locally.

Not reviewed: no neutral review was made (the diff is text of helps and documents, and the code that adds the shared
notes is a few lines). The helps are not read in full by a reviewer, which the issue asked for: opened none, left to the
user.

Tokens:
- worker: none (the supervisor did the ticket itself, at the user's request);
- reviewer: none;
- supervisor: not measured for this ticket.
