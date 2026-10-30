---
name: ticket
description: Open the logbook issue of a piece of work and start it in a worktree and a branch of its own. Use when a feature, a fix or any change is asked, when something is left to do that would have been a TODO (it becomes an issue), or when the user asks for a ticket, an issue or a branch.
argument-hint: '<title of the work> [--issue-only]'
---

# Ticket

**START NOW. Do not greet, do not ask what to work on. Your first action is the pre-flight below.** Opening the ticket is
autonomous: execute end-to-end without confirmation, and stop only on a refusal below.

An issue is a file of the logbook (`.claude/rules/logbook.md` holds its format, its name and its size). Every piece of
work starts the same way: the issue is committed on `main`, then the work gets a worktree and a branch of its own. A
one-line fix gets a one-line issue.

`--issue-only`: only step 1. For work left for later (what would have been a `TODO`, a defect found on the way that is
too large to carry): the issue is opened and committed, nothing is started, the report gives its file name.

## Procedure

### 0. Pre-flight (refuse if any check fails)

```bash
git rev-parse --verify main && git status --short logbook
```

- No `main` with a commit. Refuse: "`main` has no commit: nothing to branch from."
- Uncommitted changes under `logbook/`. Refuse: "The logbook has uncommitted changes: commit or stash them first."
- Already in a ticket worktree (`git rev-parse --git-dir` differs from `git rev-parse --git-common-dir`) and no
  `--issue-only`. Refuse: "Already in the worktree of another ticket: do not nest. Finish it, or open the ticket with
  `--issue-only`."

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

Inside a ticket worktree (only possible with `--issue-only`) the commit lands on that branch, not on `main`: say so in
the report.

With `--issue-only`: report the file name and stop.

### 2. The worktree and the branch

`EnterWorktree` with the name `<slug>`: the date and time of the file name stay out of the branch name. Check the base:

```bash
test "$(git merge-base HEAD main)" = "$(git rev-parse main)"
```

- Not the case: `git rebase main` (the branch holds nothing yet, nothing to lose).

A new worktree starts from `main`, which must hold the issue: that is why the commit comes first. If an `origin` remote
exists, `git push -u origin HEAD` publishes the branch; without one, skip it.

### 3. Report

The issue (file name), the branch and the path of the worktree. Then the work starts.

## Forbidden

- Writing a time that did not come from `date`.
- A second issue for a subject that has one open.
- `git add -A`, `git add .`, or committing anything but the issue file on `main`.
- Closing an issue: it is closed once its `Done when` points are verified, as the logbook rule says, not here.
- Pushing to `main`.
- French in the issue, the commit or the branch name: translate.

## Quick reference

| Step           | Command                                                         |
| -------------- | --------------------------------------------------------------- |
| Pre-flight     | `git rev-parse --verify main && git status --short logbook`     |
| Existing issue | `grep -ril "<keywords>" logbook/issues 2>/dev/null`             |
| Time           | `date +%d-%m-%Y-%H%M`                                          |
| Commit         | `git add "logbook/issues/open/<file>" && git commit -m "…"`     |
| Worktree       | `EnterWorktree` named `<slug>`                                  |
