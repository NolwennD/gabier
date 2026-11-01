# The help lists every refusal a command can answer

Opened: 01-11-2026 09:05
On hold: review suggestions of `help-of-a-command-asked-with-its-flag` that break no `Done when` point and no use met yet; lifted if
one is met while using gabier.

Found in the review of `help-of-a-command-asked-with-its-flag`. All in the module `cli` (`Command.java`, `docs/commands/`,
`HelpFlagTest`, `CommandRefusalsTest`).

Done when:
- [ ] The pages of `rename`, `organize-imports`, `extract-variable` and `extract-method` (and so their help) list the keys of
      `EditingErrorKey` that their flow can answer and they omit: `editing-write-failed`, `editing-read-failed`,
      `editing-rollback-incomplete`, `editing-unsupported-encoding` (`EditingException.java:27,75,83,93`). Case: `gabier rename`
      on a read-only file answers `editing-write-failed`. `diagnostics` is checked for `editing-unsupported-encoding`.
- [ ] A test fails when a key a command can answer is on neither its page nor its help (today `CommandRefusalsTest` only compares the
      help with the page); find a way that does not copy the list.
- [ ] `HelpFlagTest` runs `rename --token --help` (the case `docs/commands/help.md` promises) and `gabier help rename --help`, whose
      answer (the help of `help`) is pinned or decided.
- [ ] `bin/ci` green, coverage 100%.
