# gabier warns when orphaned caches are left on the disk

Opened: 02-11-2026 10:42
On hold: for later (the user, 02-11-2026); it waits for the batch of the jdtls workspace tickets (`02-11-2026-0900-...`, `0901`, `0902`), which creates what it watches.
Depends on: 02-11-2026-0902-jdtls-workspace-can-be-kept-between-daemons.md

An agent or a person is told when gabier left caches behind, instead of finding a full disk: the `gabier-jdtls-data-*` folders that no live jdtls uses (the kept workspaces are removed by
`02-11-2026-*-the-kept-jdtls-workspace-feature-is-removed.md`). The daemon
and the cli already know where they are and how to tell a live one (the lock of the Eclipse workspace).

Done when:
- [ ] `gabier status` (text and `--json`) and the start of a daemon report a warning, not an error and never a refusal, when
      orphaned caches exist: how many, how much space, and the command that removes them (the one of `0902`); nothing when there are
      none. The check is cheap (no jdtls, no walk of the whole cache) and never touches a folder of a live jdtls.
- [ ] Tests red first: orphans of both kinds are reported with their size; a live workspace and a kept workspace of an existing root
      are not; a cache folder that cannot be read does not make the command fail.
- [ ] `docs/daemon.md` and `docs/commands/status.md` say what is watched and what the warning looks like. `bin/ci` passes.
