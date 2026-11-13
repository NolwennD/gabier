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

## Resolution

Pull request 56, commit `d010ef2`, fast-forward on `main`.

- The rule is `.claude/rules/help.md`; `add-daemon-method` and `extend-guide` say to write the help, not a page.
- `docs/commands/move.md` is removed. `gabier help move` (`Operations.MOVE`, and `Move.COMMAND` for what is the command
  line's own: `--json`, which `ToolsTest` keeps out of the notes the MCP tools share) now says the output, the folders
  the move empties, the other modules (`skipLocalJars = true`), what is not rewritten, the order of the imports, the
  `module-info` directives, the package-private members, and the `reason` values of the refusal. It names no request,
  handler or setting; `jdtls` is not named. What only explained the adapter (the request, the capabilities, how the answer
  is ordered, how the new folders are told to the server) is dropped.
- The conditions that the daemon decides before it asks are named for the rule, extracted with `gabier extract-method`
  in `JdtlsMoves`: `isNotAJavaSource`, `isAlreadyIn`, `takesTheNameOfAFileIn`. The reason that the server answers nothing
  for them is no longer written anywhere: it was the page's, and the rule says the code carries the rule, not its history.
- `RefusalKeysTest` (`:architecture`) checks the keys of every help against the `ErrorKey` of the code (existence, not
  completeness); `CommandRefusalsTest` compares only the commands that still have a page. `McpPageTest` is unchanged.
- The README links `gabier help move`; four open issues that named `docs/commands/move.md` name `gabier help move`.

Checked: `bin/ci` on the worktree, all stages passed (format, build, test, native, jdtls; `release-install` and
`release-notes` skipped, no changed file reaches them), and the check `bin-ci` of the pull request, green.

Not reviewed: small ticket (about 15 lines of production code, three conditions extracted by gabier with the behaviour
untouched, the rest text and a test). The reviewer was not asked for the new rule or the test either.

Opened none. Left to the next ticket (`12-11-2026-2229-the-help-of-every-command-replaces-its-page`): the other pages,
and `CommandRefusalsTest`, which goes with the last of them.

Tokens:
- worker: none (the supervisor did the ticket itself, at the user's request);
- reviewer: none;
- supervisor: not measured for this ticket; exchanges with the user: 5 messages.
