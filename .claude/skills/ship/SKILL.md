---
name: ship
description: Take the branch of a ticket to main once the subagent says it is ready — have it reviewed once by a neutral Java reviewer, turn the findings into tickets, then merge it fast-forward, close the issue, clean up the worktree and move on to the next ticket. Use when the subagent reports it is ready for review, or when the user says "ship", "livre" or "merge".
argument-hint: '<branch of the ticket>'
---

# Ship

**START NOW. Do not greet, do not ask whether to continue.** Shipping is the end of the task (`.claude/rules/workflow.md`,
"Ending a task"), and it is autonomous: execute end-to-end without confirmation. Stop only on a hard error: a refusal
below, a conflict, a review that stays blocking.

The cycle of a ticket: the **subagent** does the work; when it thinks the work is ready it runs the quality script
`bin/ci` on its worktree and fixes what it shows; then it asks for a review. The **supervisor**, which runs this skill,
has the work reviewed **once** by a neutral reviewer. Reviews do not loop, at the risk of going in circles: what the
reviewer finds becomes a ticket opened on the fly and fixed later, and the ticket under review is merged as it is. Then
the supervisor closes it and moves on to the next one. **The supervisor does not run the checks again**: the subagent's
`bin/ci` summary is the evidence.

A branch is **shipped** when its commit is on `main` and its issue is in `logbook/issues/closed/`. Report "done" only
then. `main` moves by fast-forward only, one commit per ticket. There is no remote, no merge request and no pipeline yet:
`bin/ci` and the review are the whole gate.

## Procedure

### 0. Pre-flight (send the subagent back, or refuse)

```bash
git rev-parse --abbrev-ref HEAD; git worktree list --porcelain; git remote
```

- The main checkout is not on `main`. Refuse: "Run `ship` from the main checkout, on `main`."
- An `origin` remote exists. Refuse: "A remote exists: shipping through it is not defined yet, revisit this skill."
- The branch has no worktree in the list, or no commit ahead of `main` (`git rev-list --count main..<branch>`).
  Refuse: "Nothing to ship."
- The subagent's report has no `bin/ci` summary, shows a stage that failed, or does not say how each `Done when` point
  of the issue was proven. Send it back (`SendMessage`): "Run `bin/ci` and prove each `Done when` point, then ask again."
- The worktree holds uncommitted files (`git -C <worktree> status --porcelain`), the branch has more than one commit, or
  `main` moved since the branch started (`git merge-base main <branch>` is not `main`). Send the subagent back:
  "Commit or drop the files / squash into one commit / rebase on `main`, run `bin/ci` again, and ask again."
- Uncommitted files in the main checkout that the branch also changes. Refuse: "The main checkout would be overwritten."

### 1. Neutral review

The supervisor does not read the code: a fresh agent does (`general-purpose`, `model: "sonnet"`, no worktree, read-only),
one that did not do the work and has none of its reasoning. The review starts from the **business cases handled by the
tests**, not from a full read of the diff, to keep its context small. Give it only: the issue file, the range
`main..<branch>` with the output of `git diff --stat main..<branch>`, `.claude/rules/`, and this brief:

> You are a senior Java reviewer who knows the recent releases well (21 to 27: records and record patterns, sealed types,
> pattern matching for switch, unnamed variables, sequenced collections, virtual threads, structured concurrency, scoped
> values, stream gatherers, modules). Review this change without modifying any file. Start from the tests: list the
> business cases they cover and compare them with the `Done when` points and with the cases the code must handle (edge
> cases, refusals, errors); does each test fail for the right reason? Read the production code only where a test does not
> settle a case, and skip code the issue says is copied from elsewhere or generated. Also check: idiomatic modern Java
> for the module's release; each `@ExcludeFromGeneratedCodeCoverage` and `@SuppressWarnings` use (`git diff main..<branch>
> | grep`) has a real reason; the core's purity (the domain depends only on `java..` and the allowed packages); nothing
> beyond the issue. Answer short, explicit and unambiguous, in under 300 words. Either "nothing to report", or two lists,
> BLOCKING (a `Done when` point is not met) then SUGGESTIONS, one finding per line in this form: `file:line — the
> problem — the input or case that shows it`. No hedging ("maybe", "might"): a finding you cannot show with a case is
> left out. No praise, no summary of the change.

### 2. What becomes of the findings

No second review, whatever the findings.

- **A finding that shows a `Done when` point is not met**: send it to the subagent (`SendMessage`), once. It fixes it,
  runs `bin/ci` again and keeps one commit. Its fix is checked by `bin/ci`, not reviewed again.
- **A very small problem** (a few lines, one concern, no new behavior): the supervisor fixes it itself, without a
  ticket, once the ticket is merged (`.claude/rules/workflow.md`, "Tickets").
- **Any other problem** (a bug, a weakness, a latent risk): open an issue on the fly with the skill `ticket --issue-only`,
  saying what the reviewer saw, where (file and line) and how to reproduce. It is fixed later by its own ticket, not in
  this one. Group findings that are the same problem into one issue, and open none for a pure matter of taste.
- **A choice that is the user's** (a decision they took earlier, a rule to change): do not decide it, report it to the
  user.
- Every finding, and what became of it, is listed in the issue's resolution.

### 3. Merge

From the main checkout:

```bash
git merge --ff-only <branch>
```

- Not a fast-forward (`main` moved meanwhile): send the subagent back to rebase and run `bin/ci` again, then merge,
  without a new review.

### 4. Close the issue

As `.claude/rules/logbook.md` says: `mkdir -p logbook/issues/closed`, `git mv` the file, add a `## Resolution` with the
short hash of the commit, what was checked (quote the subagent's `bin/ci` summary), what is worth remembering, and the
open suggestions. Commit **only the issue file**, on `main` (never `git add -A`).

### 5. Clean up

```bash
git worktree remove <worktree> && git branch -d <branch>
```

- `git branch -d` refuses ("not fully merged") after a fast-forward: that is not expected, check before forcing.
- A leftover directory under `.claude/worktrees/` that `git worktree list` no longer shows: look at it, and delete it
  only if it holds nothing but ignored build output.

### 6. Report, then the next ticket

Report faithfully: the issue closed, the commits now on `main` (`git log --oneline main -n <count>`), the `bin/ci`
summary of the subagent, the review (blocking findings fixed, suggestions left), any unrelated bug reported by the
subagent and the issue opened for it, and anything skipped.

Then move on: take the next open issue in `logbook/issues/open/`, the one with the earliest `Opened:` line whose
`Depends on:` issues are all closed and that has no `On hold:` line, and run the skill `ticket` on it. Stop instead when the user said to stop after this
ticket, or when no open issue is ready: say so.

Never give two coupled tickets (same files, same module, same feature) to subagents at the same time: they conflict at
merge. Run them one after the other; a later issue that is coupled to an open one declares it with `Depends on:`. Only
independent tickets may run in parallel.

## Forbidden

- Running the subagent's checks again, or `bin/ci` in its place: it is the subagent's job, and its summary the evidence.
- `git push` of any kind while no remote is defined for it; any push to `main`.
- `git rebase -i`, `git commit --amend`, `--no-verify`, `--force` by the supervisor.
- A merge that is not a fast-forward.
- Merging before the subagent's `bin/ci` summary is green and the review has been done.
- A second review of the same ticket, or any loop between the reviewer and the subagent.
- Fixing a finding yourself: it becomes a ticket, or goes to the user.
- Deleting a branch, a worktree or an issue that is not this ticket's.
- Saying "done" before step 5 ended.

## Quick reference

| Step     | Command                                                              |
| -------- | -------------------------------------------------------------------- |
| Evidence | the subagent's report: `bin/ci` summary and one proof per `Done when` |
| Review   | fresh `general-purpose` agent, brief of step 1                       |
| Findings | unmet `Done when`: back to the subagent once; the rest: `ticket --issue-only` |
| Merge    | `git merge --ff-only <branch>` from the main checkout                |
| Close    | `git mv` to `closed/`, `## Resolution`, commit that file only        |
| Clean up | `git worktree remove <worktree> && git branch -d <branch>`           |
| Next     | earliest open issue, dependencies closed, not on hold: skill `ticket` |
