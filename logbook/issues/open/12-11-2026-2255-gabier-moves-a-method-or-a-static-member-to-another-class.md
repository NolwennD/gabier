# gabier moves a method or a static member to another class

Opened: 12-11-2026 22:55

An agent that splits a large class (the ticket 12-11-2026-2245-jdtls-language-server-is-split-by-family-of-operations is the
case met) moves a method or a static member to another class with one `gabier` command, which rewrites the uses and the
imports, instead of editing text. Today `gabier help --for="move methods to a new class"` abstains and the guide
(`docs/improve.md`) has no entry for it. This ticket may end in a dead end: if jdtls does not offer the move, the result is the
guide entry that says so (`By hand:`, with the reason), and no command.

Done when:
- jdtls is read and observed first (`.claude/rules/workflow.md`, "Value first": the documentation of Eclipse JDT LS and the
  source, `ripwire`; the move kinds of `java/move` and the way to list the destinations are the lead): the resolution says which
  settings or client capabilities give the move, and what jdtls offers for an instance method, a static member and a
  destination class that does not exist yet.
- If jdtls offers it: a command in the catalogue for what it offers, with its refusals as problem objects, the daemon method,
  the cli command, the MCP tool, its help, the guide entry and the contract scenario scripted into the fake and replayed on
  the real jdtls (skills `add-daemon-method`, `add-scenario`, `extend-guide`). `gabier help --for` names it for the task above.
- If jdtls does not offer it, or only part of it: the guide entry says what is by hand and why; nothing in gabier guesses or
  sorts out in advance what jdtls would get wrong (workflow rule "jdtls is never fought").
- The native image of the cli stays built (`bin/ci --stage native` green).
