# gabier warns when orphaned caches are left on the disk

Opened: 02-11-2026 10:42
On hold: for later (the user, 02-11-2026); the jdtls data-folder tickets (`0900`, `0901`) that create what it watches are closed.

An agent or a person is told when gabier left caches behind, instead of finding a full disk: the `gabier-jdtls-data-*` folders that no live jdtls uses. The daemon
and the cli already know where they are and how to tell a live one (the lock of the Eclipse workspace).

Done when:
- [ ] `gabier status` (text and `--json`) and the start of a daemon report a warning, not an error and never a refusal, when
      orphaned caches exist: how many, how much space, and the command that removes them (the one of `0902`); nothing when there are
      none. The check is cheap (no jdtls, no walk of the whole cache) and never touches a folder of a live jdtls.
- [ ] Tests red first: orphaned data folders are reported with their size; a live one is not; a cache folder that cannot be read does not make the command fail.
- [ ] `docs/daemon.md` and `gabier help status` say what is watched and what the warning looks like. `bin/ci` passes.
