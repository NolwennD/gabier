# gabier move leaves an import of the package it moves a class into

Opened: 09-11-2026 00:33

Real friction met while using gabier (ticket `the-cli-reaches-the-daemon-through-a-port`, 08-11-2026): after `gabier move` of
`DaemonClientTest`, `DaemonLauncherTest` and `WaitNoticeTest` to `eu.gabier.cli.infrastructure.secondary`, the moved files kept
an import of their own new package (`import eu.gabier.cli.infrastructure.secondary.DaemonClient;` in `DaemonClientTest`), and so did
`Start.java` after the move of the main classes. The worker removed them by hand. An agent should not have to.

Done when:
- [ ] A scenario shows the case: a class moved with a class of the same move that it uses (or that uses it), then the files of
      the moved class are compared.
- [ ] Either the import is gone after `gabier move`, or `docs/commands/move.md` and the guide say that jdtls leaves it and that
      `gabier organize-imports <file>` removes it (checked on the real jdtls), per "jdtls is never fought"
      (`.claude/rules/workflow.md`); a light workaround is allowed only in the adapter, with its reason in the resolution.
- [ ] `bin/ci` passes.
