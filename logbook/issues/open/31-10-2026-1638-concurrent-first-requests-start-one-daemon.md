# Two first requests at once start one daemon

Opened: 31-10-2026 16:38
On hold: no real use yet, lifted when agents are seen running gabier commands in parallel on a cold start (31-10-2026 refocus).

Found in the review of `gabier-starts-the-daemon-when-it-is-needed`. Two agents that run a command at the same time with no
daemon running can each start one: `DaemonLauncher.java:140-141` does `deleteIfExists` then `createFile` on the log, so the
log of the daemon that wins is unlinked and `<socket>.log` holds the refusal of the other, or the second `createFile` throws
`FileAlreadyExistsException`, reported as "cannot write the log of the daemon".

Done when:
- [ ] Two launchers started at the same time with no daemon give one daemon and two answers, and the log holds the daemon
      that serves (a test with two threads, red first).
- [ ] A test pre-creates the log as a symlink and shows it is not followed; the reuse tests assert that the pid in the log
      is unchanged; the native check "ends with stop" fails when `status` fails (`bin/ci.d/40-native`).
- [ ] `bin/ci` green, coverage 100%.
