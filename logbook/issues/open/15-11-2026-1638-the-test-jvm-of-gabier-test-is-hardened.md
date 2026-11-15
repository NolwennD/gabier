# The test JVM of `gabier test` is hardened

Opened: 15-11-2026 16:38
On hold: findings of the review of `gabier test` that break no `Done when` point; lifted when a use shows one (a test that reads
standard input, or a `gabier stop` during a run, are the likeliest).

Hardening of `ProcessTestRunner`, `TestRunnerMain`, `JUnitPlatform` and `RunnerReport` (`modules/daemon`), one point per finding.

Done when:
- The report and the log are in a private temporary folder (not a file name in the shared `/tmp`), so that nobody can plant a
  link at the report path; the temporary files are removed after a run, a timeout and a failure.
- A corrupt or forged report is a refusal `testing-run-failed`, not an internal error; the log is read by its last bytes only,
  so that a test that prints a great deal and exits does not run the daemon out of memory; a failure message is capped, with a
  visible mark.
- A test that reads standard input does not wait for the time limit; a class path that is too long for one argument still starts.
- `gabier stop`, the end of the daemon or a signal during a run ends the test JVM and its descendants; a test shows the JVM and a
  child of the test are dead after a timeout and after a stop.
- A failed result with no throwable does not end the run without a report; the listener proxy answers `hashCode`, `equals` and
  `toString`; a discovery error other than a missing engine is not read as "no engine".
- The help of `test` promises only the JUnit versions a replay covers (the tests run on JUnit 6.1.3 only), or a replay covers 5.x
  and Vintage; a `--timeout` above what the MCP call waits for (`Pacing.BOUND`) is documented or capped; the wording "killed when it
  ends with no report" of `docs/daemon.md` is true.
