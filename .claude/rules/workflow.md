# Way of working (Canon TDD + Tidy First, adapted to gabier)

## Development loop

1. Write the **test list** to cover (in the answer or in a temporary `test-list.md`), with no implementation decisions.
2. Take **a single** item, write the test, run it in a targeted way (skill `verify`) and watch it go **red for
   the expected reason**.
3. Write the minimum code to make it pass, without breaking the previous ones. Cases discovered along the way go to the
   list, not to the code.
4. Refactor if useful, tests green. Back to 2 as long as the list is not empty. A Java refactoring goes through gabier
   itself (dogfooding): the latest installed version that works, not the build in progress, so that a broken build does
   not stop its own repair. An operation gabier does not have yet is done as a text edit and becomes an issue
   (`ticket --issue-only`); no other tool, no script of any language stands in for it, and chatbot's Python driver is
   not used any more.

Let the compiler drive: a type or a method is **used** before it is created. Never write code "for later".

## Fake it until you run it

The core never waits for a real language server. Behaviour of the domain and of the application (what to ask, how to
apply the answer, how to report a refusal) is built and tested against a **hand-written fake** of the port
(`JavaLanguageServer`), scripted by the test with what the server would answer. No jdtls, no process, no network, so
`check` stays fast and a core ticket never depends on an instance running. A real server appears only at the edge: the
adapter's own tests (`integrationTest`, the jdtls probe), which prove that the real server answers the way the fake was
scripted. The same holds one level up: the daemon and the CLI are first run against the fake. A ticket that needs a
running jdtls for a point of the core is cut badly: split the core from the adapter.

A fake is a promise about the real server, and it is kept by **contract scenarios**: each answer the core relies on is
a scenario (the files of a small project, the request, the answer in domain types) written once in the test fixtures.
The fake is scripted with the scenario's answer in `check`; the integration tests of the adapter replay the same
scenario against the real server and fail if it answers differently. A scripted answer that no integration test
replays is a guess, so a ticket that adds one adds its replay, or says in its resolution why it cannot.

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
  shows (skill `verify`). Slow checks stay off the working path: while working, only targeted tests and, at most,
  `bin/ci --fast` (format, build, test); the full `bin/ci` (native image, real jdtls) is run **once**, at the end. Its
  `native` and `jdtls` stages skip themselves, with the reason in the summary, when no changed file can reach them
  (`--all` forces them); a failed stage is run again alone with `--stage <name>`, never the whole run. The gates it runs (coverage at 100%, architecture tests) and the formatting are described in
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

Every change to the code is born as a ticket, except the very small technical tasks (below). The skill `ticket` opens the issue in the logbook,
then a subagent does the work in a worktree and a branch of its own, supervised by the session that ran the skill. What
is found on the way and is too large to carry is reported to the supervisor, who opens it with `ticket --issue-only`: it
becomes a ticket, not a `TODO` and not a silent omission (`.claude/rules/logbook.md`).

**Coupled tickets are never given to subagents at the same time.** Tickets that touch the same files, the same module or
the same feature conflict when they are merged. They are done one after the other, and the order is declared with a
`Depends on:` line in the later issue. Only independent tickets may run in parallel.

**At most two tickets run at the same time** (decision of the user, 30-10-2026; a batch counts for one): the machine is
shared, Gradle, the native image and jdtls are heavy, and a third worker makes the stages crawl and the tests that depend
on timing fail. Before launching a worker, count the ones in flight; a third waits for one of them to be merged.

**Coupled and ready issues are run as one batch** (decision of the user, 30-10-2026, to save time): the open issues of
the same module that are ready are given to one subagent, in one worktree, with one `bin/ci` and one review. **One commit
per issue, always**: a ticket is one commit, batch or not, so each issue keeps its own commit, its own `## Resolution`
and every `Done when` point proven separately; each commit builds and passes its own tests. Batches of different modules, which do not touch the same files, run in parallel.

**Very small technical tasks are the supervisor's own**, without a ticket and without a subagent, to keep things fluid:
a few lines, one concern, no new behavior (tidying a build file, a typo, a version, a rule or a skill wording). The
supervisor checks the change with `bin/ci` (a build file: the proofs that its change could break), and commits it
directly on `main`. The same goes for the small findings of a review and for the friction met along the way, by a
subagent or by the supervisor (a script that fails, a rule that no longer matches the practice): when they are that
small, the supervisor fixes them itself; a bigger one becomes a ticket opened on the fly. Not when a ticket being worked on touches the same files, since that is a coupled change and would
conflict with it; not for a feature, a bug fix that needs a test of its own, or a new module: those are tickets.

Another exception, when the user says so ("no need for a ticket"): the change is committed directly on `main`, whatever
its size.

A ticket that adopts, reverses, defers or rejects a choice that the code, a rule or a document carries says why in the
`## Resolution` of its issue: the code says what the repository does, the issue says why, and what it replaced.

## Exchanges between agents

Every message between agents, in both directions and including the supervisor's own to the workers and the reviewer
(prompt, answer to a question, instruction, report, review), is short, explicit and unambiguous. It states
facts and decisions, not reasoning: what was done, what was proven and how (command, count, test name), what is asked.
A finding names `file:line`, the problem and the case that shows it. No hedging ("maybe", "might"), no praise, no
recap of what the reader already has. A question offers its options, with a recommendation. A report that is longer than
the work it reports is cut.

## Worktrees

Several sessions may write in this repository at once. The local `main` is the reference, and a ticket's worktree
branches from it, which is why the skill `ticket` commits the issue first. What the request says wins.

**A commit of `.md` files only never blocks** (issues of the logbook, rules, skills, documents): the supervisor commits it
on `main` at any time, without `bin/ci`, and the branches in flight are rebased over it by the supervisor at merge time,
with no new `bin/ci` (`.claude/skills/ship/SKILL.md`, step 0).

**Whoever works in a worktree delivers, and the supervisor merges.** The subagent hands back one commit and does not
merge; the supervisor merges it fast-forward, then removes the worktree and the branch before it reports to the user.
A new worktree needs `mise trust` once; the pre-commit hook lives in the shared `.git/hooks`, so it covers every
worktree. A merge that does not fast-forward, or a gate that stays red for a reason the branch does not explain, stops
the delivery: say so in the answer, with the branch name, never leave it silent.

## A mechanism meant to grow ships its skill

A change installing something that will be extended over time (a registry, a configuration, a list to complete; for
gabier, the catalogue of operations) also delivers the `.claude/skills/<name>/SKILL.md` describing, step by step, how to
extend it, and that skill is applied once during the implementation to prove it works.
