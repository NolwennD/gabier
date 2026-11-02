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
