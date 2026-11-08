# gabier generates the skill or the rule that teaches an agent to use it, as the project's maintainers choose

Opened: 08-11-2026 10:16

For 0.4.0 (discoverability). The maintainers of a Java project choose how their agents learn gabier, and one command
writes it: `gabier init-agent <skill|rule|snippet>` creates `.claude/skills/gabier/SKILL.md`, a rule
(`.claude/rules/gabier.md`) or the block to add to `AGENTS.md`, from the same source as `gabier help agents`. Today
the maintainers copy text by hand and it is out of date at the next version.

Done when:
- [ ] the command writes the chosen form in the project (the target folder is an option; a skill has its frontmatter, a rule
      its heading, the snippet a marked block `<!-- gabier:start -->…<!-- gabier:end -->` that a second run replaces); a file
      that exists and that gabier did not write is refused with a key, `--force` overwrites; `--dry-run` prints what it
      would write;
- [ ] the content comes from the one source of `gabier-prints-the-instructions-for-an-agent`; the skill and the rule hold
      what the project chooses to include (the commands, with `--only=<command>,…` to cut), and say the version of gabier
      that wrote them;
- [ ] a test per form and per refusal on a temporary project; `gabier help init-agent`, `docs/cli.md` and the README say it;
      `bin/ci` passes.

Depends on: gabier-prints-the-instructions-for-an-agent (the source of the text).
