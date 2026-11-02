# gabier move removes the folders it empties

Opened: 02-11-2026 19:17

Met while renaming the base package (`rename-the-base-package`): after `gabier move` of every class of a package, the
old folder tree (`fr/craft/gabier/...`) stays, empty, and has to be removed by hand. A move that empties the source
folder of a package removes it, and its parents up to the source root when they are empty too.

Done when:
- a move that empties a package folder leaves no empty folder between it and the source root; a folder that still holds a
  file (a `package-info.java` not moved, a resource) stays; a dry run removes nothing;
- `docs/commands/move.md` says so; `bin/ci` passes.
