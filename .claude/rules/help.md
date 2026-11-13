---
paths:
  - 'modules/cli/src/main/java/**'
  - 'docs/**'
---

# The help is the documentation of a command

An agent that uses a command reads `gabier help <command>` and nothing else. The help of a command is its only
documentation: there is no page `docs/commands/<command>.md` for a command, and none is written. (`docs/commands/` still
holds the pages of the commands not yet moved to this rule, and the pages of what is not a command; the ticket
`the-help-of-every-command-replaces-its-page` removes the first.) The pages of what is not a command (`mcp.md`,
`daemon.md`, `protocol.md`, the concept pages) are written by hand and stay.

- **What the help says**: the usage, the output (what is printed, and with `--json`), the refusals (each key, and when it
  is answered) and the limits (what the command does not do, and what the agent runs after it). Each fact once, in short
  sentences, in the present tense, with no hedging ("may", "should", "probably").
- **What it does not say**: a request, a handler, a setting or a class of the language server, and how the adapter works.
  The agent needs what it sees and what it does next, not the way gabier gets there. A fact that only explains the
  adapter is dropped, not moved to a comment or a page. The help names `jdtls` only for a limit that is the server's
  and that the agent must work around, and says it in one sentence.
- **Where it lives**: the summary, the notes, the refusals and the parameters in `Operations` (written once, the front
  ends build from it); the usage and the example in the command (`Move.COMMAND`). Nothing is written twice: a page, a
  comment or a skill that repeats the help is removed, and links to the help (`gabier help <command>`).
- **The reason of a rule of the code is a method named for the rule**: a refusal that the daemon decides before it asks
  the language server (a file that is not Java, a name taken in the package) is a condition extracted into a method whose
  name says the rule (`isNotAJavaSource`, `takesTheNameOfAFileIn`), not a comment and not a paragraph of a page.
  `gabier extract-method` does the extraction.
- **The refusals are tested against the code, not against a page**: `RefusalKeysTest` (`:architecture`) fails when a key
  in the help is the key of no `ErrorKey` of the code. `CommandRefusalsTest` still compares the commands that keep a page
  with it, until their pages are removed.
- **A change to a command updates its help** in the same commit (`.claude/skills/ticket/SKILL.md`): the notes and
  refusals in `Operations`, never a page.
