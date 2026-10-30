# The tests of the served project leave no process behind, and the docs say what the daemon does

Opened: 30-10-2026 21:43

Found in the review of `the-daemon-serves-a-project-through-jdtls` and of the batch that merged before it:
- `InstalledDaemonTest.close()` (`InstalledDaemonTest.java:132`) destroys only the handles seen so far and never calls
  `remember()` first: if `ask("diagnostics")` throws, the daemon is killed with SIGKILL and jdtls and its children stay
  running; the loop at lines 194-203 stops as soon as one process is seen, which can be the launcher script alone, so
  "no process left" can pass with a leaked java process;
- `FakeServedMain.requireNewOrEmpty`: a dangling symlink passes `Files.notExists` and the later `createDirectories` ends in
  a stack trace instead of the refusal; the new `catch (IllegalArgumentException)` in `main` also catches one thrown by
  `new FakeJavaLanguageServer` or `scripted` and reports it as a refusal;
- the standard error of the daemon holds jdtls's lines raw (`Main.java`, the `jdtls:` lines) and
  `WARNING: Unsupported notification method: language/eventNotification` from LSP4J;
- the README does not say that `gabier stop` can last about a minute when jdtls ignores the shutdown (10 s for the
  shutdown, 10 s for the exit, 10 s per process of the tree), nor that the project defaults to the working directory and
  that jdtls importing an untrusted checkout runs its Gradle or Maven build at the first request.

Done when:
- `InstalledDaemonTest` kills every jdtls process whatever fails (`remember()` first in `close()`) and waits for the java
  process, not only the launcher, before `stop`; shown by a test that fails on purpose once, then restored;
- the fake-served folder check treats a dangling symlink as not empty and the catch is scoped to that check, a test each;
- the lines of jdtls on the standard error of the daemon go through `TerminalText.neutralise`; the LSP4J warning is
  silenced or explained (its logger level), with the reason;
- the README says the three things above;
- `bin/ci` green, coverage 100%.

Depends on: 30-10-2026-2143-jdtls-restarts-and-says-when-the-import-is-done (same module, same files).
