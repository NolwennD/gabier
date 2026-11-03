# gabier rename follows a package into every source set

Opened: 03-11-2026 11:08
On hold: hardening of `rename-accepts-a-package`; the user lifts it if two calls (main, test) get in the way.

`gabier rename` on a package renames it in the source folder of the clicked file only (jdtls, observed 03-11-2026): the
test source set keeps `package demo.app` in its old folder and only gains an import. The core asks jdtls from each source
folder that holds the package and merges the change sets, so that one call renames the package everywhere.

Done when:
- one `gabier rename` of a package leaves main and test in the new package and folder, `gabier diagnostics` clean;
- the second request sees the edits of the first (overlay, as the extract flows do); a scenario replays it on jdtls;
- `gabier help rename` and the docs drop the "second call" remark; `bin/ci` passes.

Depends on: rename-accepts-a-package.
