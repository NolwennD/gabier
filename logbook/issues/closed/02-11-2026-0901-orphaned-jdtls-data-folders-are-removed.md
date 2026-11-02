# The jdtls data folder of a killed daemon does not stay behind

Opened: 02-11-2026 09:01
Depends on: 02-11-2026-0900-jdtls-shares-library-indexes-between-projects.md

The real bug met while looking at the jdtls instances: `/tmp` is a tmpfs (RAM, 14.5 Go), and the `-data` folder of a
daemon that was killed without a clean stop stays in it (`gabier-jdtls-data-*`: 42 Mo from 30-10 and 700 Ko today, against
227 Mo for a live one). `scripts/stop-stray-daemons.sh` stops such daemons but removes no folder.

Done when:
- [ ] When it starts jdtls, the daemon removes the `gabier-jdtls-data-*` folders of the user that no live jdtls uses (the
      lock of the Eclipse workspace, `.metadata/.lock`, says whether it is held). It never touches the folder of a running
      jdtls, nor one that is not the user's (tests, red first, for each case).
- [ ] A `-data` kept by the option of the ticket "the jdtls workspace of a project can be kept between daemons" is never
      concerned (it is outside the temporary prefix).
- [ ] `docs/daemon.md` says it. `bin/ci` passes.

## Resolution

Commit 5497011. `bin/ci` (worker, full, on the final tree, 71b3b68): format 11s, build 20s, test 181s, native 161s, jdtls 297s, all passed; tests: cli 559, core 638, daemon 817, protocol 369, shared 236, architecture 19, 0 failures, coverage 100%. Reviewed once: one blocking finding (below), fixed by the worker; the suggestions are in `02-11-2026-1219-jdtls-workspace-lock-review-suggestions.md` (on hold). The tree `bin/ci` ran on is the final tree; commit 3 was not built on its own.

- Finding that changes the issue: jdtls 1.63 holds **no** `.metadata/.lock` (`WorkspaceLockOfJdtlsTest` asserts it on a real jdtls; its sources do not lock the instance area, the Eclipse IDE does). The daemon holds a lock of its own, a `FileLock` on `<data>/.gabier-lock` for as long as it runs jdtls (`WorkspaceLock`); the OS drops it when the daemon dies, so a killed daemon reads as unused. Limit: a jdtls left alive by a daemon killed with SIGKILL is not protected, the lock being the daemon's.
- Behaviour: at jdtls start `StaleWorkspaces.removeKilled` removes the `gabier-jdtls-data-*` folders of the user only, never a symlink, never one whose lock is held; one with no lock file and younger than the start timeout (5 min) is kept. A `-data` kept by the option of the next ticket is outside the prefix.
- Blocking finding of the review, fixed: `WorkspaceLock.isHeld` (and a refused second `acquire`) opened and closed a second channel on the lock file, which on Linux drops the lock of the whole process, so the daemon lost its own lock when its own sweep looked at its own folder, and another daemon could delete a live folder. `WorkspaceLock` now keeps a registry of the locks the JVM holds and answers from it; red tests probe the lock from another process (`WorkspaceLockTest.checkingItNeverGivesUpTheLockThisProcessHoldsForAnotherProcess`, `aSecondAttemptToTakeItNeverGivesUpTheLockThisProcessHoldsForAnotherProcess`, `JdtlsWorkspaceTest.Temporary.keepsTheLockOnItsOwnFolderForAnotherDaemonThatSweepsTheTemporaryFolder`).
- Unit tests of this ticket: `StaleWorkspacesTest.OfKilledDaemons`, `WorkspaceLockTest`, `JdtlsWorkspaceTest.Temporary`, `JdtlsSessionTest.StaleDataFolders`.

Tokens (the batch of four issues shares these figures):
- worker: 482246 (`subagent_tokens` of its last notification; the worker was resumed once, after the review, and the first notification said 446626).
- reviewer: 92192 (`subagent_tokens`).
- supervisor: 20 calls, output 27027, input + cache write 595597 (cache read 5591166); covers the supervisor session over the period of the batch (launch of the worker to now), other work included (the issue of the orphaned caches, the questions to the user); exchanges with the user: 2 messages typed, and two questions answered.
