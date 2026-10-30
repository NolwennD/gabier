---
name: ship
description: Take the branch of a ticket to main — check it, have it reviewed by a neutral Java reviewer, bring it to one commit on main, merge it fast-forward, close the issue, and clean up the worktree. Use at the end of a ticket, when the subagent reports it is done, or when the user says "ship", "livre" or "merge".
argument-hint: '<branch of the ticket>'
---

# Ship

**START NOW. Do not greet, do not ask whether to continue.** Shipping is the end of the task (`.claude/rules/workflow.md`,
"Ending a task"), and it is autonomous: execute end-to-end without confirmation. Stop only on a hard error: a refusal
below, a conflict, a red check, a review that stays blocking.

The **supervisor** runs this skill, never the subagent that did the work: the subagent hands back one commit and does not
merge. A branch is **shipped** when its commit is on `main` and its issue is in `logbook/issues/closed/`. Report "done"
only then.

How it holds together: `main` moves by fast-forward only, one commit per ticket, rebased on the `main` it lands on. There
is no remote, no merge request and no pipeline yet: the checks below are the whole gate.

## Procedure

### 0. Pre-flight (refuse if any check fails)

```bash
git rev-parse --abbrev-ref HEAD; git worktree list --porcelain; git remote
```

- The main checkout is not on `main`. Refuse: "Run `ship` from the main checkout, on `main`."
- An `origin` remote exists. Refuse: "A remote exists: shipping through it is not defined yet, revisit this skill."
- The branch has no worktree in the list, or no commit ahead of `main` (`git rev-list --count main..<branch>`).
  Refuse: "Nothing to ship."
- The worktree holds uncommitted files (`git -C <worktree> status --porcelain`). Refuse, and send the subagent back
  (`SendMessage`): "Uncommitted files: commit them or drop them."
- Uncommitted files in the main checkout that the branch also changes. Refuse: "The main checkout would be overwritten."

The issue of the ticket is the one the branch was started for (`logbook/issues/open/`).

### 1. One commit, rebased on main

Run the git commands of the branch in its worktree (`git -C <worktree> …`).

```bash
git -C <worktree> rebase main
```

- A conflict: **STOP**, no `git rebase --abort`, no `git reset --hard`. Report the files and the name of the branch; the
  worktree stays.
- More than one commit ahead of `main`: collapse them with `git -C <worktree> reset --soft "$(git -C <worktree> merge-base main HEAD)"`
  then one `git -C <worktree> commit`, its message saying **what** the ticket delivers, never how it was done.

### 2. Check

In the worktree.

1. Check the `Done when` points of the issue yourself by running the checks, not by trusting the report.
2. Run the skill `verify` on everything (`./gradlew check` and `hk check --all`, no `-q`, read the output). The gates
   (coverage at 100%, architecture tests) and the formatting are in `.claude/rules/quality.md`; run those that exist.

- Anything red: **STOP**. Nothing is merged. Send it back to the subagent (`SendMessage`), then start again at step 1.

### 3. Neutral review

Launch a fresh agent (`general-purpose`, no worktree, read-only) that did not do the work and has none of its reasoning.
Give it only: the issue file, the range `main..<branch>`, `.claude/rules/`, and this brief:

> You are a senior Java reviewer who knows the recent releases well (21 to 27: records and record patterns, sealed types,
> pattern matching for switch, unnamed variables, sequenced collections, virtual threads, structured concurrency, scoped
> values, stream gatherers, modules). Review this change without modifying any file. Check: correctness and edge cases;
> idiomatic modern Java for the module's release; test quality (does each test fail for the right reason); every
> `@ExcludeFromGeneratedCodeCoverage` use is truly too complicated or too unlikely to test; the core's purity (the domain
> depends only on `java..`); simplicity (nothing beyond the issue). Report blocking findings first, then suggestions, each
> with file and line, in under 300 words, or say "nothing to report".

- A blocking finding goes back to the subagent (`SendMessage`), then start again at step 1 and review the new commit with
  the same reviewer. Suggestions are not applied: they go to the user, and into the issue's resolution.

### 4. Merge

From the main checkout:

```bash
git merge --ff-only <branch>
```

- Not a fast-forward (`main` moved since step 1): start again at step 1.
- Then `./gradlew check` on `main`, to confirm what landed.

### 5. Close the issue

As `.claude/rules/logbook.md` says: `mkdir -p logbook/issues/closed`, `git mv` the file, add a `## Resolution` with the
short hash of the commit, what was checked, what is worth remembering, and the open suggestions. Commit **only the issue
file**, on `main` (never `git add -A`).

### 6. Clean up

```bash
git worktree remove <worktree> && git branch -d <branch>
```

- `git branch -d` refuses ("not fully merged") after a fast-forward: that is not expected, check before forcing.
- A leftover directory under `.claude/worktrees/` that `git worktree list` no longer shows: look at it, and delete it
  only if it holds nothing but ignored build output.

### 7. Report

Faithfully: the issue closed, the commits now on `main` (`git log --oneline main -n <count>`), the checks run and their
result, the review (blocking findings fixed, suggestions left), any unrelated bug reported by the subagent and the issue
opened for it, and anything skipped.

## Forbidden

- `git push` of any kind while no remote is defined for it; any push to `main`.
- `git rebase -i`, `git commit --amend`, `--no-verify`, `--force`.
- A merge that is not a fast-forward.
- Merging before the checks are green and the review is clean.
- Applying a reviewer's suggestion yourself: it goes to the user, or back to the subagent if blocking.
- Deleting a branch, a worktree or an issue that is not this ticket's.
- Saying "done" before step 6 ended.

## Quick reference

| Step     | Command                                                              |
| -------- | -------------------------------------------------------------------- |
| Rebase   | `git -C <worktree> rebase main`, then `reset --soft` + one commit    |
| Check    | `./gradlew check` in the worktree, `Done when` run by hand           |
| Review   | fresh `general-purpose` agent, brief of step 3                       |
| Merge    | `git merge --ff-only <branch>` from the main checkout                |
| Close    | `git mv` to `closed/`, `## Resolution`, commit that file only        |
| Clean up | `git worktree remove <worktree> && git branch -d <branch>`           |
