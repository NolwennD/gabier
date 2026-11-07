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
