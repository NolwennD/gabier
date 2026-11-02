# gabier picks the socket of the project it is run in

Opened: 02-11-2026 09:00

An agent runs `gabier <command>` in any worktree with no `GABIER_SOCKET` prefix and never reaches the daemon of another
folder. Today the default socket is one per user, so a worker that forgets the prefix talks to the daemon of another
project (`file-outside-project`) or starts, in its worktree, the daemon that the main checkout then finds busy.

Done when:
- [ ] Without `GABIER_SOCKET`, the default socket depends on the root of the project: `GABIER_PROJECT`, else the nearest
      ancestor of the working directory that holds a `.git` (a file in a worktree), else the working directory. The name
      stays readable (the folder name and a short hash of the path) and fits in 107 bytes. The cli and the daemon compute
      it the same way (`SocketLocation`).
- [ ] Two worktrees give two sockets, a subfolder gives the socket of its root, `GABIER_SOCKET` always wins (unit tests,
      red first).
- [ ] `gabier status` and `gabier stop` with no variable reach the daemon of the current project;
      `scripts/stop-stray-daemons.sh` still works unchanged (it walks every `*.sock` of the folder).
- [ ] `docs/daemon.md` ("One daemon runs per user", "The socket"), `.claude/rules/dogfooding.md` (the prefix goes, the
      worker's final `gabier stop` stays), `.claude/skills/verify/SKILL.md` and `bin/ci.d/40-native` are updated.
      `bin/ci` passes.

## Resolution

Commit 71b3b68. `bin/ci` (worker, full, on the final tree, 71b3b68): format 11s, build 20s, test 181s, native 161s, jdtls 297s, all passed; tests: cli 559, core 638, daemon 817, protocol 369, shared 236, architecture 19, 0 failures, coverage 100%. Reviewed once: one blocking finding (below), fixed by the worker; the suggestions are in `02-11-2026-1219-jdtls-workspace-lock-review-suggestions.md` (on hold). The tree `bin/ci` ran on is the final tree; commit 3 was not built on its own.

- `SocketLocation.resolve(env, uid, workingDirectory)`: `GABIER_SOCKET` wins, else `<runtime>/gabier/<name>-<hash8>.sock`; the project is `GABIER_PROJECT`, else the nearest `.git` ancestor (a file in a worktree), else the working directory, as a real path; the name is plain characters, 40 at most, and the longest path is about 82 bytes (uid of 10 digits), any longer one refused with a clear message. The cli (`Invocation`) and the daemon (`Main`) use the same function. Tests: `SocketLocationTest` (two worktrees, a subfolder, `GABIER_SOCKET`, the length), `MainTest`, `WithoutADaemonTest`, the native stage check `socketOfTheProject`.
- `scripts/stop-stray-daemons.sh` is unchanged (it walks every `*.sock`). The workers' prefix goes from `.claude/rules/dogfooding.md`; the supervisor stops a worker's daemon with `GABIER_PROJECT=<worktree> gabier stop`.
- Friction met by the worker: `gabier diagnostics` takes one file only (`02-11-2026-1219-gabier-diagnostics-takes-several-files.md`).

Tokens (the batch of four issues shares these figures):
- worker: 482246 (`subagent_tokens` of its last notification; the worker was resumed once, after the review, and the first notification said 446626).
- reviewer: 92192 (`subagent_tokens`).
- supervisor: 20 calls, output 27027, input + cache write 595597 (cache read 5591166); covers the supervisor session over the period of the batch (launch of the worker to now), other work included (the issue of the orphaned caches, the questions to the user); exchanges with the user: 2 messages typed, and two questions answered.
