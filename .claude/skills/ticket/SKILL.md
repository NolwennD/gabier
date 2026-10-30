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
subagent, answers when the subagent stops to ask, and decides what to do with what comes back. A very small technical
task (a few lines, one concern, no new behavior) is not a ticket: the supervisor does it itself, and does not run this
skill (`.claude/rules/workflow.md`, "Tickets").

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

Count the workers in flight first: **two at most** (`.claude/rules/workflow.md`, "Tickets"; a batch counts for one). With
two running, the issue stays open and waits for a merge.

Launch it with the Agent tool, `subagent_type: "general-purpose"`, `model: "sonnet"` (the user prefers a Sonnet for the
worker; use a stronger model only when the ticket is hard or the user asks) and `isolation: "worktree"`: the worktree starts from
`main`, which must hold the issue, which is why the commit comes first. The prompt is self-contained, the subagent has
none of this conversation. **Take the other ready issues of the same module with it** (`.claude/rules/workflow.md`,
"Tickets": a batch): the prompt lists every issue file, each one's `Done when` points are the scope, and the subagent
delivers one commit per issue (each one builds and passes its tests, the full `bin/ci` is run once on the last) and proves
the points of each, one by one, in its report:

- the ticket: the path of the issue file, which it reads first, and the `Done when` points as the only scope;
- the project rules: read `CLAUDE.md` if it exists and `.claude/rules/`; test first, then implement; update the rules and
  skills that its change makes outdated;
- the quality: while working it runs targeted tests, and at most `bin/ci --fast` (no native image, no real jdtls: the
  slow checks stay off the working path); when it thinks the work is ready it runs the full `bin/ci` on its worktree
  **once** (`"$JAVA_HOME/bin/java" --source 25 bin/ci` while mise does not trust the repository) and fixes what it shows
  until every stage passes or is skipped for a stated reason (the `native` and `jdtls` stages skip themselves when no
  changed file reaches them); a failed stage is run again alone with `--stage <name>`; it proves each `Done when` point
  itself, in its report;
- the delivery: all the work in **one commit**, files added by explicit paths (never `git add -A`); it does not close the
  issue and does not merge;
- the supervision (below);
- the final report: what was done, the commit hash, the `bin/ci` summary, how each `Done when` point was proven, the
  business cases handled with the test that covers each one (the reviewer starts from them), and the unrelated bugs
  found.

The prompt and every answer of the subagent follow "Exchanges between agents" (`.claude/rules/workflow.md`): short,
explicit, unambiguous. Keep the prompt short, because the subagent reads the rules and the skills itself: the path of the issue, the scope, the
points above in a line each, and only what the rules do not already say. Do not copy the rules into it.

**Supervision**, in the spirit of the advisor: the subagent works alone, but it **stops and returns to the supervisor**
instead of pushing on when:

- it is stuck, or considers an approach other than the one the ticket implies;
- it finds a bug unrelated to its ticket: it returns the description (what it saw, where, how to reproduce) and, if the
  bug does not block the ticket, it may carry on and put it in its final report instead;
- it is ready: `bin/ci` passes and each `Done when` point is proven. It returns "ready for review" with its report,
  before anything is closed or merged. It is reviewed once: only a finding showing that a `Done when` point is not met
  comes back to it, and its fix is checked by `bin/ci`, not reviewed again.

The supervisor answers by continuing the same agent (`SendMessage` with its id), so its context is kept.

### 3. What the supervisor does with a report

- **An unrelated bug**: run this skill with `--issue-only` for it, on `main`, right away; never fix it inside the
  ticket's branch.
- **A question**: answer it, or ask the user when the answer is theirs.
- **Ready for review**: run the skill `ship` on the branch the subagent reports. It does not run the checks again: it
  has the work reviewed once by a neutral agent, turns what it finds into tickets opened on the fly, then merges, closes
  the issue, cleans up the worktree and moves on to the next ticket.

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
