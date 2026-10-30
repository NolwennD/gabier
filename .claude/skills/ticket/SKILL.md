---
name: ticket
description: Open the logbook issue of a piece of work and have a subagent do it in a worktree and a branch of its own, under the supervision of the current session. Use when a feature, a fix or any change is asked, when something is left to do that would have been a TODO (it becomes an issue), when a subagent reports a bug unrelated to its ticket, or when the user asks for a ticket, an issue or a branch.
argument-hint: '<title of the work> [--issue-only]'
---

# Ticket

**START NOW. Do not greet, do not ask what to work on. Your first action is the pre-flight below.** Opening the ticket is
autonomous: execute end-to-end without confirmation, and stop only on a refusal below.

An issue is a file of the logbook (`.claude/rules/logbook.md` holds its format, its name and its size). Every piece of
work starts the same way: the issue is committed on `main`, then **a subagent** does the work in a worktree and a branch
of its own. The session that ran this skill is the **supervisor**: it does not do the work itself, it follows the
subagent, answers when the subagent stops to ask, and decides what to do with what comes back. A one-line fix gets a
one-line issue.

`--issue-only`: only step 1. For work left for later (what would have been a `TODO`), and for a bug a subagent reports
as unrelated to its ticket: the issue is opened and committed on `main`, nothing is started, the report gives its file
name.

## Procedure

### 0. Pre-flight (refuse if any check fails)

```bash
git rev-parse --verify main && git status --short logbook
```

- No `main` with a commit. Refuse: "`main` has no commit: nothing to branch from."
- Uncommitted changes under `logbook/`. Refuse: "The logbook has uncommitted changes: commit or stash them first."
- Inside a worktree (`git rev-parse --git-dir` differs from `git rev-parse --git-common-dir`). Refuse: "This is a ticket
  worktree: a subagent working a ticket does not open tickets. Report to your supervisor."

### 1. The issue

Look first for an issue already open on the subject, never two for one piece of work:

```bash
grep -ril "<two or three keywords of the work>" logbook/issues 2>/dev/null
```

- One matches: it is the ticket, read it, skip the creation and use its title for the slug.
- None matches: take the real time with `date +%d-%m-%Y-%H%M`, then write
  `logbook/issues/open/<DD-MM-YYYY-HHMM>-<slug>.md` as the logbook rule says. The title is English, an outcome like a
  commit subject; the slug is the title in lowercase kebab-case, 40 characters at most; the content is what the user
  asked for, in a few lines: the goal, then a `Done when:` list.
  Commit **only that file** (never `git add -A`):

```bash
git add "logbook/issues/open/<file>"
git commit -m "Open issue: <title>"
```

With `--issue-only`: report the file name and stop.

### 2. The subagent

Launch it with the Agent tool, `subagent_type: "general-purpose"`, `model: "sonnet"` (the user prefers a Sonnet for the
worker; use a stronger model only when the ticket is hard or the user asks) and `isolation: "worktree"`: the worktree starts from
`main`, which must hold the issue, which is why the commit comes first. The prompt is self-contained, the subagent has
none of this conversation:

- the ticket: the path of the issue file, which it reads first, and the `Done when` points as the only scope;
- the project rules: read `CLAUDE.md` if it exists and `.claude/rules/`; test first, then implement;
- the delivery: all the work in **one commit**, files added by explicit paths (never `git add -A`); it does not close the
  issue and does not merge;
- the supervision (below);
- the final report: what was done, the commit hash, how each `Done when` point was checked, and the unrelated bugs found.

**Supervision**, in the spirit of the advisor: the subagent works alone, but it **stops and returns to the supervisor**
instead of pushing on when:

- it is stuck, or considers an approach other than the one the ticket implies;
- it finds a bug unrelated to its ticket: it returns the description (what it saw, where, how to reproduce) and, if the
  bug does not block the ticket, it may carry on and put it in its final report instead;
- it is done: it reports before anything is closed or merged.

The supervisor answers by continuing the same agent (`SendMessage` with its id), so its context is kept.

### 3. What the supervisor does with a report

- **An unrelated bug**: run this skill with `--issue-only` for it, on `main`, right away; never fix it inside the
  ticket's branch.
- **A question**: answer it, or ask the user when the answer is theirs.
- **Done**: run the skill `ship` on the branch the subagent reports. It checks the work, has it reviewed by a neutral
  agent, merges it into `main`, closes the issue and cleans up the worktree.

### 4. Report to the user

The issue (file name), the branch, the commit, and every unrelated bug with the issue opened for it.

## Forbidden

- Doing the ticket's work in the supervisor session: the subagent does it.
- Writing a time that did not come from `date`.
- A second issue for a subject that has one open.
- `git add -A`, `git add .`, or committing anything but the issue file on `main` when opening a ticket.
- Closing or merging a ticket other than through the skill `ship`.
- Pushing to `main`.
- French in the issue, the commit or the branch name: translate.

## Quick reference

| Step           | Command                                                         |
| -------------- | --------------------------------------------------------------- |
| Pre-flight     | `git rev-parse --verify main && git status --short logbook`     |
| Existing issue | `grep -ril "<keywords>" logbook/issues 2>/dev/null`             |
| Time           | `date +%d-%m-%Y-%H%M`                                          |
| Commit         | `git add "logbook/issues/open/<file>" && git commit -m "…"`     |
| Subagent       | Agent tool, `model: "sonnet"`, `isolation: "worktree"`          |
| Continue it    | `SendMessage` to the agent id                                   |
