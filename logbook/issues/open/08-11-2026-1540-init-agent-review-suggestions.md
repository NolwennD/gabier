# gabier init-agent keeps the mode, the line endings and the other blocks of the file it writes into (review suggestions)

Opened: 08-11-2026 15:40
On hold: findings of the review of `gabier-generates-a-skill-or-a-rule`; none breaks a `Done when` point; they matter when a maintainer's `AGENTS.md` is not plain (a mode, CRLF, a documented marker) or when two processes write at once.

- `AgentFile.java:109`: `ATOMIC_MOVE` of a fresh temp file gives the destination the umask mode: `chmod 600 AGENTS.md` then `init-agent snippet` loses the mode (a hard link is broken too). The docs say so; no test covers it. Copy the POSIX permissions of the existing file onto the temp file.
- `AgentFile.java:75`: only the first `START` is handled: a file with two blocks has the first replaced and the second left stale; a marker quoted in a code fence (an `AGENTS.md` that documents the markers) is replaced as if it were the block. Refuse when `START` occurs more than once, or skip fenced markers.
- `AgentFile.java:86`, `AgentForm.java:65`: the block is joined with `\n`; a CRLF file gets mixed line endings on append and on replace (a BOM file is kept as is).
- `AgentFile.java:69-109`: between the `exists` check and `ATOMIC_MOVE` a file created by another process is clobbered by the `CREATED` case; `CREATE_NEW` or a hard-link publish would close it.
- `AgentFile.java:75-83`: a `SKILL.md` that holds a gabier block but lost its frontmatter (or was built from `snippet`) gets only its block replaced: a skill with no frontmatter that never loads.
- `InitAgentTest.java:163`: `aWriteLeavesNoTemporaryFileBehind` exercises success only; removing the `finally` of `AgentFile.java:107-111` leaves it green. Add a failing move (a read-only folder, a destination that is a non-empty directory).
- `InitAgentTest.java:174`, `:194`: a test parameterized over three forms returns at once for `snippet`; `forceOverwritesAFileThatGabierDidNotWrite` is false for `snippet` (appended).
- `AgentInstructions.java:47`: with `--only=rename` the lead bullet keeps `extract-method`, `move`, `inline` and `create`, because a bullet is kept whole if any command it cites is chosen: say so in `gabier help init-agent`, or split that bullet in `agents.md`.
- Missing tests: a dangling link `AGENTS.md` (replaced by a regular file, not written through); `--target` that is itself a link; `--dry-run` with a link that leads out.

Done when:
- [ ] each point above is either fixed with a test, or answered in this issue's resolution with the reason; `bin/ci` passes.
