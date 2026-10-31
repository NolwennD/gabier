# `gabier` starts the daemon when it is needed

Opened: 31-10-2026 16:05

An agent runs `gabier rename` or `gabier diagnostics` in a project folder and gets the answer, without starting
`gabier-daemon` by hand first (today: `daemon-not-running` until someone does, README "Command line"). The cli starts the
daemon for the current project when none answers on the socket, waits until it accepts requests, then sends the request.
`status` and `stop` still never start one. A mise task installs `gabier` and `gabier-daemon` on the PATH, so that an
agent can call them by name in any checkout.

Done when:
- [ ] A request command (`diagnostics`, `rename`) with no daemon running starts `gabier-daemon` for the project it is run
      in (the folder rules of the README: argument, `GABIER_PROJECT`, else the current folder), waits for the socket, and
      answers; shown against the fake daemon in `GabierTest` and in the native stage of `bin/ci.d/40-native`.
- [ ] A daemon that does not come up (not found, exits at once, no socket before a stated delay) is a problem object with
      the reason and where its output went; nothing is left running; a running daemon is reused, never started twice.
- [ ] `status` and `stop` with no daemon still answer `daemon-not-running` at once.
- [ ] `mise run install` (name free) puts `gabier` and `gabier-daemon` on the PATH, and the README says how.
- [ ] README ("Command line", daemon section) is updated; `bin/ci` green, coverage 100%.
