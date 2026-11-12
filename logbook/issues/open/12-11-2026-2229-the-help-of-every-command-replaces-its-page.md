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
