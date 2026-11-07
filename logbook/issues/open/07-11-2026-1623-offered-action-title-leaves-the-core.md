# The core no longer reads a title of jdtls to link a command to an action (review suggestions)

Opened: 07-11-2026 16:23
On hold: finding of the review of `actions-command-link-review-suggestions`; the failure is safe (a link is lost, never a wrong one), so no use is broken today.

- `modules/core/src/main/java/eu/gabier/editing/domain/OfferedAction.java:58`: `command()` reads the title of the action
  (`startsWith("Inline ")`) to link `refactor.inline` to `gabier inline`. The Javadoc of the record says gabier does not
  read titles, and `.claude/rules/workflow.md` ("Value first") puts the specifics of jdtls in the adapter. A renamed,
  future or localized title ("Inline all occurrences") loses the link silently, with no test or scenario that notices.
  The worker took it to avoid a protocol change; the adapter already holds the same predicate
  (`JdtlsLanguageServer.java:1092`).
- `modules/core/src/testFixtures/.../CodeActionScenarios.java` (`code-actions-on-the-use-of-a-constant`):
  `EXTRACT_VARIABLE` repeats the kind already replayed by `code-actions-on-a-concatenation`; it costs one title in
  `MOST_ACTION_TITLES` (11 instead of 10).

Done when:
- [ ] the adapter decides which actions `inline` does and the core receives the answer (a field of the action, or the
      command itself), the core reads no title; the scenario `code-actions-on-a-method-that-can-be-made-static` still
      shows "Make Static" unlinked on the real jdtls;
- [ ] `EXTRACT_VARIABLE` leaves the new scenario if `ListCodeActionsTest` can do without it, and the ceiling goes down;
- [ ] `bin/ci` passes.
