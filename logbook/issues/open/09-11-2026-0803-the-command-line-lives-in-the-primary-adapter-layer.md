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
