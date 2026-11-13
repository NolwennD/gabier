# The lock test covers `published` and `answered`

Opened: 13-11-2026 02:18
On hold: finding of the review of the split of `JdtlsLanguageServer` (ticket 12-11-2026-2245) that breaks no `Done when`
point; lifted when a change to the facade shows the gap matters.

`JdtlsLanguageServerLockTest` checks the public methods of the facade only. `published(List<Path>)` (package-private, called
by `JdtlsCodeActions.codeActionEdit`) and the private `answered(Map)` are not checked: removing `synchronized` from either
one passes the test, although the families call them under the facade lock.

Done when:
- The lock test fails when `published` or `answered` loses `synchronized`, shown by removing it in the working copy and
  watching the test go red for that reason.
- The test keeps naming, one by one, the methods of the facade that are not `synchronized` on purpose.
