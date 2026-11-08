# A refusal or an unknown command points the agent to gabier help --for

Opened: 08-11-2026 10:16

For 0.4.0 (discoverability). An agent that mistypes a command, or is refused because jdtls does not do what it asked,
is told where to look: `gabier help --for="<the task>"` for the task, `gabier help <command>` for the command. Today
the answer stops at the error.

Done when:
- [ ] an unknown command answers with the closest commands (when one is near) and the line `gabier help --for="<task>"`
      (a test per case: a near name, a name far from all);
- [ ] a refusal of a command whose help lists other ways (the `By hand:` cases of the guide above all) ends with the
      pointer to `gabier help <command>`; one place builds the line, not each command; the keys and the exit codes do not
      change;
- [ ] `gabier help` and `docs/cli.md` say it; the line is on the standard error with the refusal and is part of the
      `--json` answer as a field; `bin/ci` passes.
