# Closing a jdtls session ends jdtls when its launcher died first

Opened: 30-10-2026 14:02

`JdtlsSession.close()` reads the process tree (`process.descendants()`) only when it runs. If the launcher died
before, the tree is empty: the java child of jdtls keeps running and the data folder is deleted under it.

Done when:
- [ ] A test starts a stand-in launcher with a child (`sh -c "sleep 300 & wait"`), kills the launcher, calls `close()`
      and asserts the child is dead and the data folder gone (red first for this reason).
- [ ] The descendants are recorded once the session is ready and killed with the current ones at close.
- [ ] `bin/ci` green, 100% coverage, no exclusion.

## Resolution

Merged as `818e4cd` (rebased once on `main` after the worker's first report: the architecture-rule ticket had landed).

Checked by the worker's `bin/ci` (full, on the rebased commit): format, build, test (100% coverage included), native and
jdtls all passed. `JdtlsSessionTest.Close.endsTheChildrenOfALauncherThatDiedBeforeTheClose` starts a stand-in launcher
`sh -c "sleep 300 & wait"`, kills the launcher, and asserts after `close()` that the child is dead and the data folder
gone; red first with a `TimeoutException` (the orphan stayed alive). `JdtlsSession` records `process.descendants()` in
`started` at the end of `awaitReady()` and `close()` kills the union of the recorded, the current descendants and the
launcher.

Review (one pass): no BLOCKING finding. Suggestions, grouped in the issue
`closing-a-session-ends-what-the-launcher-started-late`: a launcher that dies during the start leaves nothing recorded;
a process started after the recording survives; the test leaks its orphan when an assertion fails first; a javadoc line
over 120 columns.

Tokens:
- worker: 81772 (its last completion notification; it was resumed by messages)
- reviewer: 44338
- supervisor: 74271 output, 148726 input + cache write over 71 calls (cache read 8897513), supervisor session over the
  period of the ticket (from the launch of the worker; three other tickets ran in parallel and the rest of the session is
  included); exchanges with the user: 2 messages
