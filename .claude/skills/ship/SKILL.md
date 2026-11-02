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
then. `main` moves by fast-forward only, one commit per ticket. The repository is on GitHub (`origin`) and `main` is
protected: the branch goes through a pull request, and `bin/ci`, the check of the pull request and the review are the
gate (`.claude/rules/workflow.md`, "Ending a task").

## Procedure

### 0. Pre-flight (send the subagent back, or refuse)

```bash
git rev-parse --abbrev-ref HEAD; git worktree list --porcelain; git remote
```

- The main checkout is not on `main`. Refuse: "Run `ship` from the main checkout, on `main`."
- No `origin` remote, or the local `main` is not the `origin/main` (`git fetch origin && git rev-list --count main...origin/main`
  is not 0). Refuse: "`main` and `origin/main` differ: push or pull `main` first."
- The branch has no worktree in the list, or no commit ahead of `main` (`git rev-list --count main..<branch>`).
  Refuse: "Nothing to ship."
- The subagent's report has no `bin/ci` summary, shows a stage that failed, or does not say how each `Done when` point
  of the issue was proven. Send it back (`SendMessage`): "Run `bin/ci` and prove each `Done when` point, then ask again."
- The worktree holds uncommitted files (`git -C <worktree> status --porcelain`), the branch has more than one commit per issue (a batch has one commit per issue, no more), or
  `main` moved since the branch started (`git merge-base main <branch>` is not `main`). Send the subagent back:
  "Commit or drop the files / squash into one commit / rebase on `main`, run `bin/ci` again, and ask again."
  **Exception, so that a text change never blocks** (decision of the user, 30-10-2026): when `main` moved only by files
  that are not code (`git diff --name-only <merge-base> main` lists only `.md` files, issues, rules and skills), the
  supervisor rebases the branch itself (`git -C <worktree> rebase main`), does not ask for `bin/ci` again and does not
  send the worker back. A rebase that stops on a conflict goes back to the worker.
- Uncommitted files in the main checkout that the branch also changes. Refuse: "The main checkout would be overwritten."

### 0b. Pull request

```bash
git push -u origin <branch>
gh pr create --base main --head <branch> --title "<subject of the ticket's commit>" --body "Issue: <issue file>"
```

A batch has one pull request, its commits one per issue. The check of the pull request runs while the review is made.
While it waits, the `.md` commits of the supervisor (the issues of the findings) stay on the local `main`: pushing
`main` then would leave the branch behind it, and its rebase would start the check again. At the merge they are put back on top of
the branch (`git rebase <branch> main`, local commits only, so the hash of the ticket's commit stays) and pushed with it.

### 1. Neutral review

A review is made on code that can go to `main` without breaking anything: the worker's `bin/ci` is green on this very
commit (the pre-flight checks it, and sends the worker back after a rebase that touched code). What a tool proves is not
re-read by a person: the reviewer's job starts where the tools stop.

**A small ticket is not reviewed** (decision of the user, 30-10-2026, to save time): when the diff changes at most 30
lines of production code (tests, docs, rules, skills and build files do not count) and none of them parses an input
that comes from outside, does I/O, concurrency or a process, or handles anything security-relevant (a path, a
terminal, a socket, a permission), or when the change is a rename or a move with the behaviour untouched. Say so in the
`## Resolution` ("not reviewed: small ticket, <reason>") and skip the rest of this step. A doubt means a review.

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
> beyond the issue. The branch has passed `bin/ci` and rebuilds, so what the compiler, Error Prone, NullAway, JaCoCo and
> the architecture tests prove is not yours to check: a removed method still used, a missing import, a type error, a
> dependency rule, a line or a branch without a test. Do not search the rest of the project for usages and do not run a
> build. Only what a tool cannot see is yours: a case no test covers, a test that cannot fail, a wrong or misleading
> behaviour, a reason that is not real. Answer short, explicit and unambiguous, in under 300 words. Either "nothing to report", or two lists,
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
  this one. Group findings that are the same problem into one issue, and the findings of one review that fall in the
  same module into one issue too (a `Done when` point per finding): one ticket, one `bin/ci`, one review. Open none for a
  pure matter of taste.
- **A choice that is the user's** (a decision they took earlier, a rule to change): do not decide it, report it to the
  user.
- Every finding, and what became of it, is listed in the issue's resolution.

### 3. Merge

From the main checkout:

Once the check of the pull request is green (`gh pr checks <branch> --watch`; a red check sends the subagent back, like a
red `bin/ci`; the check is `bin-ci`):

```bash
git merge --ff-only <branch>
```

`main` is pushed at the end of step 4, with the closing of the issue: GitHub then marks the pull request merged.

- Not a fast-forward (`main` moved meanwhile): send the subagent back to rebase and run `bin/ci` again, push the branch
  again (`git push --force-with-lease origin <branch>`, the only rewrite allowed, on the ticket's branch), then merge, without
  a new review.

### 4. Close the issue

As `.claude/rules/logbook.md` says: `mkdir -p logbook/issues/closed`, `git mv` the file, add a `## Resolution` with the
short hash of the commit, what was checked (quote the subagent's `bin/ci` summary), what is worth remembering, and the
open suggestions, then the `Tokens:` list (`.claude/rules/logbook.md`): one line for the worker, one for the reviewer
(each the sum of the `subagent_tokens` of its completion notifications) and one for the supervisor, measured on the
transcript of the session over the period of the ticket (from the launch of the worker, not from the opening of the issue which may have waited in the queue, to now):

```bash
J=$(ls -t "${CLAUDE_CONFIG_DIR:-$HOME/.claude}/projects/$(git rev-parse --show-toplevel | tr / -)"/*.jsonl | head -1)
A=$(date -u -d "<ISO time of the launch of the worker>" +%Y-%m-%dT%H:%M:%S); B=$(date -u +%Y-%m-%dT%H:%M:%S)
jq -s --arg a "$A" --arg b "$B" '[.[] | select(.message.usage != null and .timestamp >= $a and .timestamp <= $b)]
  | {calls: length, input: (map(.message.usage.input_tokens) | add), cache_write: (map(.message.usage.cache_creation_input_tokens) | add),
     cache_read: (map(.message.usage.cache_read_input_tokens) | add), output: (map(.message.usage.output_tokens) | add)}' "$J"
# messages the user typed in the period (not agent reports, not system notices)
jq -c --arg a "$A" --arg b "$B" 'select(.type == "user" and .timestamp >= $a and .timestamp <= $b and .isSidechain != true)
  | .message.content | if type == "string" then . else (map(select(.type == "text").text) | join(" ")) end
  | select(length > 0 and (test("^(Another Claude session|\\[SYSTEM|<)") | not))' "$J" | wc -l
```

The supervisor line gives `calls`, `output` and `input + cache_write` (the cache reads are named apart: they dominate
and cost little), then `exchanges with the user: <n> messages` (a floor: a message sent while the supervisor was
working is not counted). The tokens include that talk and the other tickets of the same period; say so, so that a
statistic can tell the tickets with exchanges from those without. Commit **only the issue file**, on `main` (never
`git add -A`), then `git push origin main` (never `--force`).

### 5. Clean up

```bash
git worktree remove <worktree> && git branch -d <branch>
```

- `git branch -d` refuses ("not fully merged") after a fast-forward: that is not expected, check before forcing.
- A leftover directory under `.claude/worktrees/` that `git worktree list` no longer shows: look at it, and delete it
  only if it holds nothing but ignored build output.

Then stop the Gradle daemon, which every worktree shares and which keeps the workers of its builds (javac, tests, Kotlin)
alive for hours (decision of the user, 31-10-2026: a ticket often lasts more than 30 minutes, so the 30 seconds of a cold
start cost little):

```bash
./gradlew --stop
```

Only when no other worker is in flight: its build runs on the same daemon. With one in flight, skip it and say so in the
report; the last ticket to be shipped stops the daemon.

Then stop the gabier daemons of worktrees that no longer exist (a worker that ended without stopping its own):

```bash
sh scripts/stop-stray-daemons.sh
```

It leaves alone the daemon of every project that exists, those of the workers in flight included, so it is safe whatever
is running.

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
- A rewrite of `main` on `origin`; `--force` without `-with-lease`, or on anything but the ticket's branch; a push of `main`
  that holds a code commit whose pull request has a check that is not green; any push by a worker.
- `git rebase -i`, `git commit --amend`, `--no-verify` by the supervisor.
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
| PR       | `git push -u origin <branch>`, `gh pr create --base main`           |
| Merge    | check green, then `git merge --ff-only <branch>` from the main checkout |
| Close    | `git mv` to `closed/`, `## Resolution`, commit that file, push `main` |
| Clean up | `git worktree remove <worktree> && git branch -d <branch>`           |
| Next     | earliest open issue, dependencies closed, not on hold: skill `ticket` |
