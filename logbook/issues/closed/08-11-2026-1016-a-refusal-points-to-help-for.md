# A refusal or an unknown command points the agent to gabier help --for

Opened: 08-11-2026 10:16

For 0.4.0 (discoverability). An agent that mistypes a command, or is refused because jdtls does not do what it asked,
is told where to look: `gabier help --for="<the task>"` for the task, `gabier help <command>` for the command. Today
the answer stops at the error.

Done when:
- [x] an unknown command answers with the closest commands (when one is near) and the line `gabier help --for="<task>"`
      (a test per case: a near name, a name far from all);
- [x] a refusal of a command whose help lists other ways (the `By hand:` cases of the guide above all) ends with the
      pointer to `gabier help <command>`; one place builds the line, not each command; the keys and the exit codes do not
      change;
- [x] `gabier help` and `docs/cli.md` say it; the line is on the standard error with the refusal and is part of the
      `--json` answer as a field; `bin/ci` passes.

## Resolution

Done in ea80e46 (pull request 22, merged fast-forward). One class of the cli, `HelpPointer`, decides and builds the line:
a first word that names no command (a near name, a far one, no word, `--bogus`, `gabier help <unknown>`) gets
`Not sure which command? Ask for the task: gabier help --for="<task>"`, and a refusal where jdtls or the edit declined the
operation gets `Usage and other ways: gabier help <command>`. In text the line is a second line on the standard error; with
`--json` it is a last member `help` of the problem object (`Problem.toJson(String help)`). Keys, exit codes and the daemon's
messages do not change. `docs/cli.md`, `docs/commands/help.md`, `gabier help` and the skill `add-daemon-method` say it.

- Checked: the worker's full `bin/ci` (format, build, test, native, jdtls passed; release-install skipped, no changed file
  reaches it), and `bin-ci` green on the pull request after the rebase on the new `main` (run 42477090074, 12 min 21 s,
  every stage). The first full run had failed on a Checkstyle `DeclarationOrder` in `CliTest`, fixed.
- Choice that was the worker's, to settle: the 14 keys of `DECLINED_OPERATIONS`. It is in the issue
  `help-pointer-review-suggestions` (on hold).
- Review (neutral, once): nothing blocking. Five suggestions, all in `08-11-2026-1115-help-pointer-review-suggestions.md`
  (on hold): only 7 of the 14 keys are tested, the set of keys itself, the skill's wording for the key names, an invalid JSON
  example in `docs/cli.md`, and `usage-error` tested with one command only.
- Not a finding but worth remembering: the check of this pull request failed six times before it was green, on 504 answers
  of the download servers (see `the-ci-installs-its-jdk-without-sdkman` and `the-ci-caches-the-tools-it-downloads`).

Tokens:
- worker: 187160 tokens (1 notification).
- reviewer: 69286 tokens (1 notification).
- supervisor: 175 calls, 111946 output tokens, 228354 input + cache write (26664242 cache reads, apart), from 10:18 to 12:58
  on 08-11-2026 (09:18 to 11:58 UTC); the period holds the other worker of the batch, the two CI tickets and the wait for the
  checks, so the figure is not this ticket's alone; exchanges with the user: 5 messages.
