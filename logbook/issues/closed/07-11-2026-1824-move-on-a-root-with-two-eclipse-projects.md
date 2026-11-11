# gabier move works on a root that holds several Eclipse projects, or says why it cannot

Opened: 07-11-2026 18:24

Met by the worker of `move-updates-the-exports-of-the-module-info` (07-11-2026): `gabier move --dry-run <files>
<package>` through the cli, on a root that holds two Eclipse projects (`shared/` and `app/`, each with its own `.project`
and `.classpath`, `GABIER_PROJECT` set to that root), answers `Internal server error … (internal-server-error)` with no
description, and the daemon log holds nothing about it. The same request through the adapter, in the replay of the
`MODULES` scenario, works. An agent that moves a class in such a layout cannot tell what went wrong.

Done when:
- [ ] reproduced on the real jdtls first (a root with two Eclipse projects, a move from one to a package of the other,
      and within one), with the cause named in the issue's resolution (jdtls, the daemon, or the layout of the root);
- [ ] `gabier move` on that root either moves, or is refused with a key and a description that say why; never
      `internal-server-error` with no description; the limit, if it stays, is in `gabier help move`;
- [ ] a scenario and its replay show it, or the resolution says why not; `bin/ci` passes.

Related: `the-daemon-logs-the-cause-of-an-error` (the log of the cause), `the-daemon-does-not-write-into-nested-projects`.

## Resolution

Closed without a commit: not reproduced (11-11-2026). The worker of the batch tried two Eclipse projects `shared/` and `app/` under one root (each with `.project`, `.classpath` and a `module-info.java`), with the installed daemon and with the daemon of its worktree: `gabier move --dry-run` of `Money` from `shared` to `demo.app` and to `demo.shared.money`, and of `Till` from `app` to `demo.shared`, with and without module attributes, absolute and relative paths, the working directory inside one project, `GABIER_PROJECT` set. Every call answered with edits and the daemon log held no failure. The replay of `MODULES` (`move-a-class-that-another-module-uses`) already shows a move across two projects. The probable cause, not proven, is the code in progress of `move-updates-the-exports-of-the-module-info` at the time, or a stale daemon. A failure of that kind would now be logged (`the-daemon-logs-the-cause-of-an-error`) and, as a -32603, refused as `language-server-request-failed`. To open again with the command, the log and the files if it comes back.

Tokens:
- worker: 116793 (one worker for the batch of four issues, three commits)
- reviewer: 125503 (one review of the batch)
- supervisor: 34 calls, output 22845, input + cache write 241562 (cache reads 4118124), over the period of the batch (launch of the worker 11-11-2026 01:47 UTC to the merge), other work included; exchanges with the user: 0 messages
