# The daemon finds the pinned jdtls by itself

Opened: 02-11-2026 22:55

An agent can use gabier on any Java project, not only in the gabier checkout. Today only the `mise.toml` of gabier
exports `GABIER_JDTLS`, so in another project the daemon inherits no such variable and the first request fails with
`GABIER_JDTLS is not set`. When the variable is not set, the daemon falls back to the jdtls that `mise run jdtls:install`
installs, `~/.local/share/jdtls/<pinned version>/bin/jdtls` (the version of `mise.toml`, known to the build). If that
jdtls is missing, it falls back to a `jdtls` on the `PATH`. `GABIER_JDTLS` still wins when it is set.

Done when:
- A request run in a project that has no `mise.toml`, with `GABIER_JDTLS` unset and the pinned jdtls installed, is
  answered.
- With `GABIER_JDTLS` set, its launcher is the one started, whatever is installed.
- With no variable, no pinned jdtls and no `jdtls` on the `PATH`, the refusal names the three places looked at and
  `mise run jdtls:install`.
- The pinned version that the daemon uses comes from `mise.toml` at build time, never from a second copy of the number.
- `docs/daemon.md` and the README say where the daemon looks for jdtls.
