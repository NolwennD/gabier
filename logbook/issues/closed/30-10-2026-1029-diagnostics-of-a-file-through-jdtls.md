# The jdtls adapter gives the diagnostics of a file

Opened: 30-10-2026 10:29

The jdtls adapter implements the diagnostics of the port `JavaLanguageServer` (already defined and used by the core
with a fake): it opens the file, waits for the publication that follows its opening, then for jdtls to be quiet, and
answers in the domain types. This is the logic of the probe (`Diagnostics` and the settled wait), moved from the
`integrationTest` source set to the main code, the probe using it. It is the minimum every LSP server offers
(`publishDiagnostics`), and what the agents need after each edit.

Done when:
- on the real jdtls (the probe, with a temporary broken copy of a source of the checkout), the adapter answers an empty
  list for a clean file and the errors of a broken one, and fails with the typed refusal of the port for a file outside
  the project or a jdtls that is still importing;
- the contract scenarios of the diagnostics (those the core's fake is scripted with) are replayed against the real
  jdtls in an integration test, and any difference between its answer and the scenario's fails the test;
- the unit tests of the adapter need no jdtls (an in-process fake language server over piped streams) and the coverage
  stays at 100%; what the core already proves with the fake of the port is not proved again here;
- no LSP4J outside `daemon`, the architecture tests green;
- the scenarios of the fixtures of the core are replayed through a harness that writes their project, starts the real
  adapter on it and compares the answer; the refusals (outside the project, not ready) are mapped from what jdtls really
  does, as the `Scenarios` Javadoc says; and since the catalogue now has its replay, the skill
  `.claude/skills/add-scenario/SKILL.md` describes step by step how to add a scenario and its replay, and is applied once
  to add one (the rule "a mechanism meant to grow ships its skill").

Depends on: jdtls-adapter-starts-and-awaits-jdtls (same adapter and same probe code).

## Resolution

Done in df0c1b9.

- `bin/ci` (worker): format, build, test, jdtls passed; native skipped ("no changed file reaches the native cli"). Tests: cli 9, core 219, daemon 131, protocol 4, shared 236, architecture 16, 0 failures. JaCoCo 100% line and branch, no coverage exclusion added.
- Real jdtls: the replay (`ScenarioReplayTest`) of every diagnostics scenario (clean, error, warning, outside, not ready) matches; changing an expected message turned it red. The skill `add-scenario` was applied once (scenario FILE_WITH_A_WARNING).
- Choices: `HexagonalArchTest.shouldNotDependOnOtherBoundedContextDomains` skips the `daemon` context, because the daemon implements the port of `core` and so depends on `editing.domain` (ticket 1028 planned the adjustment). `quickProbeTest` is gone: every integration test now starts jdtls, `check` only compiles them and `jdtlsProbe` runs them. Outside the project, jdtls does not refuse (it opens a non-project file); the adapter refuses a file not under a folder jdtls imported. `NOT_READY` covers the initial start only.
- One replay run timed out once (300 s) under a machine load of about 10 caused by other projects' jdtls: external load, not a defect.
- Review (once): no blocking finding. Suggestions: the `daemon` exemption is wider than needed (issue opened); a missing file under the project is reported as outside the project (issue opened); two inline qualified names in `ScenarioReplayTest` (fixed by the supervisor).

Tokens:
- worker: 285,090
- reviewer: 100,829
- supervisor: 26 calls, 20,014 output, 30,403 input + cache write, 3,527,238 cache read; exchanges with the user: 3 messages (a floor). The period runs from the launch of the worker (14:03) to the closing, and includes talk with the user (rules on tokens and on agent exchanges).
