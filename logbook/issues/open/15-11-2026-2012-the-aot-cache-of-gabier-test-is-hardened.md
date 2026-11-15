# The AOT cache of `gabier test` is hardened

Opened: 15-11-2026 20:12
On hold: findings of the review of the AOT cache (PR 62) that break no `Done when` point; lifted when a use shows one (two
`gabier test` calls at once on one project, or a project whose library list changes from call to call, are the likeliest).

Hardening of `AotCache`, `ProcessTestRunner` and the build of `modules/daemon`, one point per finding.

Done when:
- Pruning the stale caches of a project does not delete the files of another key's creation in progress (two calls at once with
  different keys); a test covers it.
- The `.refused` marker is written only for a failure that cannot recur; a timeout, an interruption and a full disk do not block
  the key for good; a test covers each.
- A project whose library list varies from call to call, or that two daemon versions share, does not remake a cache of 6 to 8 s
  at every call, or the help and `docs/daemon.md` say that it does; an orphan temporary file of a killed daemon is removed.
- The help of `test` and `docs/daemon.md` say that a resource of the same name (`junit-platform.properties`, a `META-INF` file)
  is now taken from a library or from the daemon jar before the project's own, not only classes.
- `ProcessTestRunner` takes no `Optional` as a parameter (`.claude/rules/`); the toolchain of the test JDK in
  `modules/daemon/build.gradle.kts` is resolved lazily, not when `tasks.test` is configured.
- The key reads the size and the date of a jar in one read, and a test covers a jar of the class path that is a folder in the
  order of the class path.
