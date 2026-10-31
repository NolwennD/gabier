# The socket server bounds its connections and cleans up only what is its own

Opened: 30-10-2026 22:26
On hold: hardening with no effect on what an agent can do with gabier (a local, single-user daemon); taken up after the
parity with the Python driver (31-10-2026 refocus).

Found in the review of the batch `a-line-over-the-limit-is-refused`, `uptime-does-not-depend-on-the-wall-clock` and
`failed-restrict-leaves-no-socket-behind`:
- `DaemonServer.acceptClients` has no cap on the connections and no read timeout: N connections that each send
  1,048,575 bytes with no line break, or one byte per second, hold N virtual threads and about 2 MiB each (the line
  and its copy as a `String`) until they disconnect;
- `DaemonServer.java:215-216`: if `listener.bind` throws (a path over 107 bytes, an address in use) the opened
  `ServerSocketChannel` is never closed; the socket file must not be deleted in that case (after a lost race it belongs
  to the other daemon);
- `DaemonServer.java:235`: the delete after a failed `restrict` is not scoped to our file: another process that unlinks
  our socket, then a second daemon binding the path, has its socket removed by `deleteIfExists`; a regular file put there
  meanwhile too. Compare the file key taken just after `bind` with the one at cleanup time;
- `LineReader.java:52` counts a `\r` in the limit while the Javadoc and the README say the line break is not counted:
  1,048,576 bytes followed by `\r\n` are refused, the same followed by `\n` are read;
- `SocketGuard` lost `final` only so that tests can subclass it: an interface with a default implementation, or a
  function passed to `open`, keeps the production code final;
- the test of the uptime says its clock wraps around but advances only 2.5 s from 30 s before `Long.MAX_VALUE`: it never
  wraps; `neverReadsMoreThanOneByteOverTheLimitBeforeRefusing` asserts `<= 8192` (the fill of a `BufferedInputStream`),
  so it passes for any internal limit below that.

Done when:
- the number of simultaneous connections is capped and a connection that sends nothing for a stated time is closed; a
  test with a cap of 2 (a third is refused or waits, the limit stated) and a client that goes quiet, shown on a real
  socket; the values are stated in the README;
- a `bind` that fails closes the listener and leaves the file of the other daemon alone, a test each;
- the cleanup after a failed `restrict` deletes our socket only (file key), a test with a socket replaced meanwhile;
- the limit counts the line without its break, `\r\n` included, or the wording says what is counted, with a test on the
  `\r\n` case;
- `SocketGuard` is final again, the tests use a seam that is not inheritance;
- the two tests are made to prove what their names say (the clock really wraps, the reader's own count is asserted);
- `bin/ci` green, coverage 100%.
