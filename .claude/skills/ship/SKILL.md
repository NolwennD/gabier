---
name: ship
description: Take the branch of a ticket to main once the subagent says it is ready — have it reviewed by a neutral Java reviewer, send the findings back to the subagent, then merge it fast-forward, close the issue, clean up the worktree and move on to the next ticket. Use when the subagent reports it is ready for review, or when the user says "ship", "livre" or "merge".
argument-hint: '<branch of the ticket>'
---

# Ship

**START NOW. Do not greet, do not ask whether to continue.** Shipping is the end of the task (`.claude/rules/workflow.md`,
"Ending a task"), and it is autonomous: execute end-to-end without confirmation. Stop only on a hard error: a refusal
below, a conflict, a review that stays blocking.

The cycle of a ticket: the **subagent** does the work; when it thinks the work is ready it runs the quality script
`bin/ci` on its worktree and fixes what it shows; then it asks for a review. The **supervisor**, which runs this skill,
has the work reviewed by a neutral reviewer and sends the findings back to the subagent, which takes them into account
and runs `bin/ci` again. Once the reviewer has nothing blocking left, the supervisor merges, closes the ticket and moves
on to the next one. **The supervisor does not run the checks again**: the subagent's `bin/ci` summary is the evidence.

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

Launch a fresh agent (`general-purpose`, no worktree, read-only) that did not do the work and has none of its reasoning.
Give it only: the issue file, the range `main..<branch>`, `.claude/rules/`, and this brief:

> You are a senior Java reviewer who knows the recent releases well (21 to 27: records and record patterns, sealed types,
> pattern matching for switch, unnamed variables, sequenced collections, virtual threads, structured concurrency, scoped
> values, stream gatherers, modules). Review this change without modifying any file. Check: correctness and edge cases;
> idiomatic modern Java for the module's release; test quality (does each test fail for the right reason); every
> `@ExcludeFromGeneratedCodeCoverage` use is truly too complicated or too unlikely to test; the core's purity (the domain
> depends only on `java..`); simplicity (nothing beyond the issue). Report blocking findings first, then suggestions, each
> with file and line, in under 300 words, or say "nothing to report".

### 2. The findings go back to the subagent

- **Blocking findings**: send them to the subagent (`SendMessage`). It fixes them, runs `bin/ci` again, keeps one commit,
  and asks again. Then send the new commit to the **same reviewer** and repeat from here, until it has nothing blocking.
- **Suggestions**: they are not applied. They go to the user, and into the issue's resolution.

### 3. Merge

From the main checkout:

```bash
git merge --ff-only <branch>
```

- Not a fast-forward (`main` moved meanwhile): send the subagent back to rebase and run `bin/ci` again, then review again.

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
`Depends on:` issues are all closed, and run the skill `ticket` on it. Stop instead when the user said to stop after this
ticket, or when no open issue is ready: say so.

## Forbidden

- Running the subagent's checks again, or `bin/ci` in its place: it is the subagent's job, and its summary the evidence.
- `git push` of any kind while no remote is defined for it; any push to `main`.
- `git rebase -i`, `git commit --amend`, `--no-verify`, `--force` by the supervisor.
- A merge that is not a fast-forward.
- Merging before the subagent's `bin/ci` summary is green and the review has nothing blocking.
- Fixing a finding yourself: it goes back to the subagent, or to the user when it is a suggestion.
- Deleting a branch, a worktree or an issue that is not this ticket's.
- Saying "done" before step 5 ended.

## Quick reference

| Step     | Command                                                              |
| -------- | -------------------------------------------------------------------- |
| Evidence | the subagent's report: `bin/ci` summary and one proof per `Done when` |
| Review   | fresh `general-purpose` agent, brief of step 1                       |
| Findings | `SendMessage` to the subagent, then the same reviewer again          |
| Merge    | `git merge --ff-only <branch>` from the main checkout                |
| Close    | `git mv` to `closed/`, `## Resolution`, commit that file only        |
| Clean up | `git worktree remove <worktree> && git branch -d <branch>`           |
| Next     | earliest open issue whose dependencies are closed, skill `ticket`    |
