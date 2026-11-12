# The tests stream loses no change in its races

Opened: 13-11-2026 00:01
On hold: findings of the review of `gabier affected-tests --watch` (PR 52) that break no `Done when` point; lifted when a use
shows one (two watchers, or a watcher and a call, on a project that changes fast, is the likeliest).

The review found cases of `WatchAffectedTestsMethod` and `ProjectFeed.subscribeTests` that no test covers.

Done when:
- A request of `watch-affected-tests` that is refused (`already-watching`, or a connection that listens to nobody) does not use
  up the changes of the daemon: the next call that is answered still lists their tests; a test sends the second request after
  an edit and then asks for the tests of the changes.
- A watcher that starts on code that does not compile is never told the tests and then a refusal for code that compiles by
  then: the order of the first read, of the subscription and of the first refusal is fixed, or an older refusal never replaces
  a newer answer; a test covers the interleaving of a disk change between the two.
- A watcher that joins while the feed is reading does not read the tests at the same time as the feed, or the page says what
  it prints in that case and why; a test of `ProjectFeed` covers it.
- The two `@SuppressWarnings("unchecked")` of the test peers of the daemon carry the reason, in a comment next to them.
