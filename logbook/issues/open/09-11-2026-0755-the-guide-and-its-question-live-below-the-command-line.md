# The guide and its question live below the command line

Opened: 09-11-2026 07:55
Depends on: a-command-runs-an-operation-through-one-entry-point

Step 5 of the plan that prepares the cli for an MCP facade (structure only). `HelpText` both renders the help and works
out the answer to `help --for`; the guide and its ranking sit outside the layers. A `guide` tool of MCP must ask the
same question without the command line.

Done when:
- `Guide`, `GuideSection`, `GuideSections` and the `ranking` package are in `eu.gabier.cli.domain` (the loading of
  the resource stays in an adapter);
- the calculation of the answer to `help --for` is in the application layer, a function of the question and the guide;
  `HelpText` only renders it;
- `gabier help --for="..."` and `gabier help guide` print exactly what they print today;
- the guide tests pass unchanged apart from their package; `docs/cli.md` and `.claude/skills/extend-guide/SKILL.md`
  name the new places;
- `bin/ci` is green.
