# Way of working (Canon TDD + Tidy First, adapted to gabier)

## Development loop

1. Write the **test list** to cover (in the answer or in a temporary `test-list.md`), with no implementation decisions.
2. Take **a single** item, write the test, run it in a targeted way (skill `verify`) and watch it go **red for
   the expected reason**.
3. Write the minimum code to make it pass, without breaking the previous ones. Cases discovered along the way go to the
   list, not to the code.
4. Refactor if useful, tests green. Back to 2 as long as the list is not empty. A Java refactoring goes through jdtls,
   not through text editing: with gabier itself once it can do it, until then with chatbot's `jdtls-driver.py`.

Let the compiler drive: a type or a method is **used** before it is created. Never write code "for later".

## Absolute prohibitions

- Deleting or weakening a test or an assertion to get to green.
- Copying the computed value into the expected value.
- Faking an implementation (a stub returning the expected answer) and presenting it as real.
- Turning the whole list into tests before making one of them pass.
- Adding an unrequested feature "because it is the logical next step". Closed scope: this, only this.
- Bypassing a gate: `git commit --no-verify`, lowering the coverage threshold, or an `@ExcludeFromGeneratedCodeCoverage`
  without a real reason (`.claude/rules/quality.md`).
- Leaving a `TODO` in code headed for `main`: what is left becomes a logbook issue (`ticket --issue-only`) or a line in
  the answer.

## Structure vs behavior

- A change is either **structure** (rename, extract, move, tidying: identical behavior) or **behavior**. Inside a
  ticket, never mix the two in the same step.
- Tidy first: if a structural change makes the behavioral change obvious, do it **before**. Otherwise after, later,
  never: it is an economic choice, not a dogma.
- A ticket is delivered as **one commit** (`.claude/skills/ticket/SKILL.md`). So the two kinds are separated in the
  order of the work, not in the history; a purely structural change worth its own line in the history is a ticket of
  its own.
- Large change = parallel implementation and progressive switch, never a leap.
- Duplication: a hint, not an order. No abstraction before three real occurrences.

## Before saying "it's done"

- The targeted test of the touched code is green **and** its output has been read (no `-q`).
- The subagent that does the ticket runs `bin/ci` on its worktree when it thinks the work is ready, and fixes what it
  shows (skill `verify`). The gates it runs (coverage at 100%, architecture tests) and the formatting are described in
  `.claude/rules/quality.md`. The supervisor does not run these checks again: the `bin/ci` summary in the subagent's
  report is the evidence.
- The neutral review has been done once (skill `ship`, step 1). Reviews do not loop: a problem it finds becomes a ticket
  opened on the fly, fixed later; only a finding showing that a `Done when` point is not met comes back to the subagent.
- Report faithfully: a red test is said to be red, a skipped step is said to be skipped.
- The work is committed **and merged** into `main`, the issue is closed. A branch left standing in a worktree is not
  done.

## Ending a task

A task is done once the supervisor has closed the ticket and merged it with the skill `ship`. In a ticket, committing
the work is part of the job: do it without being asked and without offering it as a question, and report "done" only
afterwards, with the commits now on `main`.

Not green (a red test, a blocked step)? Do not commit and do not merge, and say so in the answer.

Exceptions: the user restricted the scope of what to commit, asked for a review first, or said to keep the branch (then
say in the answer that the worktree is still standing, and where).

`git push` has one target: the branch of the ticket, and only when an `origin` remote exists. Never `main`.

## Tickets

Every change to the code is born as a ticket, a short fix included. The skill `ticket` opens the issue in the logbook,
then a subagent does the work in a worktree and a branch of its own, supervised by the session that ran the skill. What
is found on the way and is too large to carry is reported to the supervisor, who opens it with `ticket --issue-only`: it
becomes a ticket, not a `TODO` and not a silent omission (`.claude/rules/logbook.md`).

**Coupled tickets are never given to subagents at the same time.** Tickets that touch the same files, the same module or
the same feature conflict when they are merged. They are done one after the other, and the order is declared with a
`Depends on:` line in the later issue. Only independent tickets may run in parallel.

One exception, only when the user says so ("no need for a ticket"): a small change to configuration, documentation or a
rule is committed directly on `main`.

A ticket that adopts, reverses, defers or rejects a choice that the code, a rule or a document carries says why in the
`## Resolution` of its issue: the code says what the repository does, the issue says why, and what it replaced.

## Worktrees

Several sessions may write in this repository at once. The local `main` is the reference, and a ticket's worktree
branches from it, which is why the skill `ticket` commits the issue first. What the request says wins.

**Whoever works in a worktree delivers, and the supervisor merges.** The subagent hands back one commit and does not
merge; the supervisor merges it fast-forward, then removes the worktree and the branch before it reports to the user.
A new worktree needs `mise trust` once; the pre-commit hook lives in the shared `.git/hooks`, so it covers every
worktree. A merge that does not fast-forward, or a gate that stays red for a reason the branch does not explain, stops
the delivery: say so in the answer, with the branch name, never leave it silent.

## A mechanism meant to grow ships its skill

A change installing something that will be extended over time (a registry, a configuration, a list to complete; for
gabier, the catalogue of operations) also delivers the `.claude/skills/<name>/SKILL.md` describing, step by step, how to
extend it, and that skill is applied once during the implementation to prove it works.
