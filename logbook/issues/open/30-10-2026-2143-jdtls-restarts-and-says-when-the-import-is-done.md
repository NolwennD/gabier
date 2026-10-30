# The daemon restarts a jdtls that died and waits for the end of the import

Opened: 30-10-2026 21:43

Found in the review of `the-daemon-serves-a-project-through-jdtls`:
- `JdtlsLanguageServer.state()` depends only on the start future (`JdtlsLanguageServer.java:96`) and `ProjectServer` retries
  only on `FAILED`: after `kill -9` of a ready jdtls, `status` says `ready` and every `gabier diagnostics` fails until the
  daemon is restarted;
- "ready" is a project listed plus the same list two seconds later (`JdtlsSession.java:156-166`), and the list stays fixed:
  jdtls that lists module A and imports module B three seconds later makes the session ready, and `diagnostics` of a file
  of B is refused as outside the project for good; a folder with no Gradle, Maven or Eclipse build lists nothing, so the
  first request waits the whole five minutes;
- a start interrupted by `close()` ends with a null message: a request waiting during `stop` gets "jdtls did not start:
  null" (`JdtlsLanguageServer.java:127`);
- when the launcher script exits at once, the message does not give its exit code, which `JdtlsWatch` is meant to report.

Done when:
- a jdtls that died while ready is seen as dead: `status` says so (`failed`, with the reason) and the next request
  starts it again; a test with a fake that dies, then the stage `jdtls` of `bin/ci` with a real `kill -9`;
- readiness comes from jdtls's own end-of-import signal (its status notification or the end of the import job), not from
  a list that is stable twice; a test on the fake with a module listed late, red first; a folder with nothing to import
  is refused at once with a problem object that says there is no build to import, not after five minutes;
- a request waiting while the daemon stops gets a message that says the daemon is stopping;
- a launcher that exits at once is reported with its exit code;
- `bin/ci` green, coverage 100%, nothing in `check` needs jdtls.
