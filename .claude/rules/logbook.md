# Logbook issues

The project's issues are plain files in `logbook/issues/`. Follow this when opening, editing or
closing one. To open an issue and start its work, use the `ticket` skill.

- **Where**: open issues in `logbook/issues/open/`, closed ones in `logbook/issues/closed/`. The
  `closed/` folder does not exist until the first closing: run `mkdir -p logbook/issues/closed`.
- **File name**: `DD-MM-YYYY-HHMM-issue-title.md`, the title in kebab-case. Take the time from
  `date +%d-%m-%Y-%H%M`; never invent it. The day comes first, so file names do not sort
  chronologically from one month to the next: use the `Opened:` line when the order matters.
- **Content**: a title, an `Opened: DD-MM-YYYY HH:MM` line, a short paragraph on the goal, a
  `Done when:` list of checkable points, and a `Depends on:` line only if needed. About ten lines.
- **Size**: small, concrete, doable in a short time. State the goal and how to check it, not the
  code to write. Give exact lines only for a typo or a trap that would otherwise bite.
- **Pace**: open one or two issues at a time, when their turn comes. No backlog in advance.
- **Closing**: only once every `Done when` point is verified. `git mv` the file to `closed/`, add a
  `## Resolution` section saying what was done and the short hash of the fixing commit, and commit.
- **Language**: English, like the rest of the repository.
