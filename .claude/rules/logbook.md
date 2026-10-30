# Logbook issues

The project's issues are plain files in `logbook/issues/`. Follow this when opening, editing or
closing one. To open an issue and start its work, use the `ticket` skill.

- **Where**: open issues in `logbook/issues/open/`, closed ones in `logbook/issues/closed/`. The
  `closed/` folder does not exist until the first closing: run `mkdir -p logbook/issues/closed`.
- **File name**: `DD-MM-YYYY-HHMM-issue-title.md`, the title in kebab-case. Take the time from
  `date +%d-%m-%Y-%H%M`; never invent it. The day comes first, so file names do not sort
  chronologically from one month to the next: use the `Opened:` line when the order matters.
- **Content**: a title, an `Opened: DD-MM-YYYY HH:MM` line, a short paragraph on the goal, a
  `Done when:` list of checkable points, and a `Depends on:` line only if needed: when another issue must be closed first, in particular when the two are
  coupled (same files, same module, same feature) and so must not run at the same time. About ten lines.
- **On hold**: an `On hold:` line (below the `Opened:` line) says why an issue must wait for the user, or for something
  else. The skill `ship` does not pick it as the next ticket and nothing starts it; the user lifts the hold, and the
  line is removed.
- **Size**: small, concrete, doable in a short time. State the goal and how to check it, not the
  code to write. Give exact lines only for a typo or a trap that would otherwise bite.
- **Pace**: open one or two issues at a time, when their turn comes. No backlog in advance.
- **Unrelated bugs**: a bug found while working on another ticket, and not needed for its `Done
  when` points, is not fixed in that ticket. A subagent working a ticket **reports it to its
  supervisor** (what it saw, where, how to reproduce) and does not open the issue itself; the
  supervisor opens it on `main` with the `ticket` skill and `--issue-only`. A session with no
  supervisor opens it itself. If the bug blocks the ticket, the subagent says so and stops instead
  of working around it silently.
- **Closing**: only once every `Done when` point is verified. `git mv` the file to `closed/`, add a
  `## Resolution` section saying what was done and the short hash of the fixing commit, and commit.
- **Tokens**: the `## Resolution` of a ticket closed from 30-10-2026 on ends with a `Tokens:` list, one line per agent
  that worked on it: the worker, the reviewer, and the supervisor. Closed tickets are not filled in afterwards. A
  subagent's figure is the `subagent_tokens` of its completion notification (the sum of its notifications when it was
  resumed). The supervisor's figure is measured, not estimated (`.claude/skills/ship/SKILL.md`, step 4), and says that
  it covers the supervisor session over the period of the ticket, other work included.
- **Language**: English, like the rest of the repository.
