# gabier create makes a package-info.java

Opened: 04-11-2026 09:35

An agent that adds a package creates its `package-info.java` (the project rule: every package with classes has one,
`@NullMarked`) with `gabier create`, not by hand. Met on 04-11-2026 by the worker of
`gabier-help-answers-a-question-about-a-task`: `gabier create <file> <class|interface|record|enum>` has no form for
`package-info.java`, so the file of the new package `eu.gabier.cli.ranking` was written by hand.

Done when:
- [ ] `gabier create [--dry-run] <dir>/package-info.java package` writes the file with the `package` line and the
  `@org.jspecify.annotations.NullMarked` annotation the architecture test demands, and nothing else; `gabier help
  create`, `docs/commands/create.md` and the refusals agree; a scenario replayed on the real jdtls or the reason why
  not;
- [ ] `bin/ci` passes.
