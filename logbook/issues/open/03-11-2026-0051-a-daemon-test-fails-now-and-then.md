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
