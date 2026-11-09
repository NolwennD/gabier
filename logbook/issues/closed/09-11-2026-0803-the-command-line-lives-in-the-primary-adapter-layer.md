# The command line lives in the primary adapter layer

Opened: 09-11-2026 08:03

Tidy first for the MCP facade (plan step 1b, structure only). The commands, `Capability`, `Capabilities`, `Arguments`,
`Invocation`, `HelpText` and the like sit flat in `eu.gabier.cli`, which belongs to no layer of
`HexagonalArchTest`: when step 2 puts the rendering in `application`, the layered rule flags every command that calls it.
The target of the plan has the command line in `infrastructure.primary`.

Done when:
- every class of the command line (commands, `Capability`, `Capabilities`, `Command`, `Arguments`, `Invocation`, `Refusal`,
  `HelpText`, `Help*`, `Guide*`, `InitAgent*`, `TextResource`, the usage errors, and their tests) is in
  `eu.gabier.cli.infrastructure.primary`, moved with `gabier move`; only `Gabier` (composition root, which builds the
  socket adapter) and what it needs to start stay in `eu.gabier.cli`, and the entry point still starts the cli exactly as
  before (module-info, native-image configuration and resource paths follow);
- `ranking` is left where it is (issue "the guide and its question live below the command line" moves it);
- no new exemption in `HexagonalArchTest`: the existing one for `Gabier` covers what it needs, or the resolution says
  why one more is unavoidable;
- the cli tests pass unchanged apart from their package; `gabier help` output is identical;
- `docs/cli.md`, `.claude/skills/add-daemon-method/SKILL.md` and `.claude/skills/verify/SKILL.md` name the new package;
- `bin/ci` is green, native stage included.

## Resolution

Commit 34bbc9e (PR #33, batch of three issues). Every class of the command line and its tests moved to `eu.gabier.cli.infrastructure.primary` with `gabier move`; `ranking` stayed. `Gabier` stays in `eu.gabier.cli` as the composition root (`main`, `run`, builds the socket adapter) and hands the `Daemon` port to a new `CommandLine` in the primary layer, which holds the former dispatch and the report of a failure. Resources, the `copyGuide` target and `resource-config.json` follow their classes (the two `.md` paths checked in the jar).

- Help output identical: `help` and `--json help` for the index and 22 pages snapshotted before and after, `diff -r` empty.
- Exemption: the layered rule counts a class outside the layers as an accessor, so the entry point calling a primary class is flagged like its call to the socket adapter. The exemption of step 1 covered only the secondary adapters; a second `ignoreDependency`, narrowed to `Gabier` to `CommandLine`, was unavoidable. It has no liveness test yet (suggestion, on hold). Reversal of nothing else: the rule is otherwise unchanged.
- Why this ticket: the plan put the command line in `infrastructure.primary`; step 2 could not place the rendering in `application` while the commands sat outside the layers, and a transitional exemption was rejected for this move.
- `bin/ci` of the worker, run once on 97a62bd: format, build, test, native, jdtls passed; release-install skipped (no changed file reaches `install.sh`). Check `bin-ci` of PR #33 green (11m56s).
- Review (neutral, once): nothing blocking. Seven suggestions, grouped in the on-hold issue `operation-descriptor-review-suggestions` (command-line words in the descriptor notes, wire form of the array parameters, dead `required` flag, tests of the descriptor, liveness test of the new exemption); the fourth copy of the parameter names in each `Map.of` is the subject of the next issue of the plan.
- Frictions of `gabier move`: ~27 same-package imports left in moved tests (already the issue of 7116939), a class name in a string literal (`@MethodSource`) not rewritten (documented), no import added where the tests now need `Gabier`. No command moves a method to another class.

Tokens:
- worker: 335,623 (cumulative figure of its last completion notification; one resume).
- reviewer: 102,227.
- supervisor: 25 calls, output 17,642, input + cache write 32,176 (cache reads 2,821,962), covering the supervisor session from the launch of the worker (06:55 UTC) to the merge, other work included; exchanges with the user: 0 messages.
