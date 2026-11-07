# A daemon test fails now and then on a refused connection

Opened: 03-11-2026 00:51

`MainTest > Serving > servesTheWorkingDirectoryByDefaultWithoutStartingJdtls()`, in `modules/daemon`, threw a
`ConnectException` once, at its first `status()`, during a full `bin/ci` of the ticket
`03-11-2026-0029-a-release-installs-with-one-script` (a change that touches no Java). `bin/ci --stage test` alone
then passed. The test waits until the socket file exists (`start`, `MainTest.java:197`) and then connects at once. A
socket file that exists before the daemon accepts connections, or one left from another test, would make the
connection fail. A test that fails without a cause in the change makes `bin-ci` red for nothing.

Done when:
- The cause is shown: a run that reproduces the failure (a loop of the test, or a load), or the reading of the start
  of the daemon that explains it.
- The test, or the daemon if it is the cause, no longer depends on that timing. The same loop passes, for example 200
  runs of the class under load.

## Resolution

Done in ef60592.

- Cause, by reading: the JDK binds a Unix socket and listens in two calls (`ServerSocketChannelImpl`, bind then
  `Net.listen`), and `DaemonServer.open` binds first, so the socket file exists before the daemon accepts.
  `MainTest.start` waited for the file and connected at once. By a throwaway client (a loop that waits for the file, then
  connects): refused 8 and 6 times in 3000; with a client that waits until a connection succeeds: 0 in 3000.
- Fix: `MainTest.start` waits until a connection is accepted (`listening`), as `DaemonLauncher` does. The daemon is
  unchanged.
- **Not shown**: the failure of the test itself was never reproduced; a loop of 400 runs of the test passed before and
  after the fix, not measured under load. The proof is the reading and the throwaway client.
- Left aside, reported by the worker: `SocketGuard.clearStale` deletes a socket file that refuses connections, so two
  daemons started at the same instant can delete each other's socket between bind and listen (noted in
  `main-test-wait-names-its-cause`).
- The batch was reviewed once (no blocking finding); the suggestions are in `guide-ranking-review-suggestions`, `offered-action-title-leaves-the-core` and `main-test-wait-names-its-cause`, all on hold. `bin-ci` of pull request 20: green. The worker rebased over the code of the GitHub-page ticket and ran `bin/ci --all` again: format, build, test, release-install, native, jdtls all passed.

Tokens (one batch of three issues, shared by the three resolutions):
- worker: 221860
- reviewer: 87714
- supervisor: 53 calls, 42849 output, 102368 input + cache write (6262353 cache reads), over the period from the launch of
  the worker to the merge, the ticket of the GitHub page and the other work included; exchanges with the user: 3 messages
