# The help is the documentation of a command

Opened: 12-11-2026 22:29

An agent that uses a command reads `gabier help <command>`, and nothing else: the page `docs/commands/<command>.md` is read by
nobody and is a second text to keep true (`CommandRefusalsTest` exists to make the two agree). The help becomes the only
documentation of a command, and what the page said that a user needs goes into the help, short, explicit and unambiguous.
This ticket sets the rule and does it for one command, `move`, as the model of the others (next ticket). The pages of what is
not a command (`mcp.md`, `daemon.md`, the concept pages) stay, written by hand.

Done when:
- The rule is written in `.claude/rules/` (a rule or a section of an existing one): the help of a command says its usage, its
  output, its refusals and its limits; it names no request, handler, setting or class of the language server; it states each
  fact once, in short sentences, with no hedging. The reason of a rule of the code is a method named for the rule, not a
  comment or a page. The skills that tell to write a page (`add-daemon-method`, `extend-guide`)
  say to write the help instead.
- `docs/commands/move.md` is removed. What a user needs from it, and the help does not say, is in `gabier help move`, worded as
  the rule says and with no mention of jdtls; what only explained how the adapter works is dropped. The reason a refusal of
  `move` exists is carried by the code that decides it: the condition is extracted into a method named for the rule (with
  `gabier extract-method`), not explained by a comment. The README and the other pages no longer link to it.
- The tests that compared the help with the pages (`CommandRefusalsTest`) do not need a page to pass for `move`, and nothing
  else breaks: the refusals of the help of `move` stay tested against the code, not against a page; `McpPageTest` is unchanged.
- `bin/ci` is green; `gabier help move` is read in full and judged by the reviewer against the rule.
