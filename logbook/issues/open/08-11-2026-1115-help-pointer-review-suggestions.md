# The pointer to gabier help is tested on every key it covers and its set of keys is settled (review suggestions)

Opened: 08-11-2026 11:15
On hold: findings of the review of `a-refusal-points-to-help-for`; none breaks a `Done when` point or a real use met so far.

- `modules/cli/src/test/java/eu/gabier/cli/PointingToHelpTest.java:25`: 7 of the 14 keys of `DECLINED_OPERATIONS` are
  tested; a typo in `language-server-ambiguous-imports`, `-empty-package`, `-invalid-name`, `-not-applicable`,
  `-not-movable-to-package`, `-unresolved-imports` or `editing-ambiguous-code-action` leaves every test green.
- `modules/cli/src/main/java/eu/gabier/cli/HelpPointer.java:19`: `language-server-invalid-name` and
  `editing-ambiguous-code-action` are errors of the input, and `gabier help <command>` lists no other way for them;
  `editing-token-not-found` and `editing-not-a-type-name` get no pointer. The issue speaks of the `By hand:` cases and
  none of the 14 keys is tied to them. The set is the worker's choice: the user settles it.
- `.claude/skills/add-daemon-method/SKILL.md`: says to add keys named `language-server-not-...`, but 5 of the 14 do not
  match that prefix; nothing fails when a new declined key is not added to the set.
- `docs/cli.md` (the `--json --bogus` example): shows `--for=\\"<task>\\"`, which is not valid JSON; the real output is
  `--for=\"<task>\"`.
- `PointingToHelpTest.java:79`: `usage-error` is tested only with `rename`; a usage error of another known command that
  comes back from the daemon is not shown to have no pointer.

Done when:
- [ ] each point above is either fixed with a test, or answered in this issue's resolution with the reason;
      `bin/ci` passes.
