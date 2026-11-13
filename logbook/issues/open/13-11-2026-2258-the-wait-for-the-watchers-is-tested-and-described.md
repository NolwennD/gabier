# The wait for the file watchers of jdtls is tested and described

Opened: 13-11-2026 22:58
On hold: review suggestions of the ticket "the installed-daemon rename test is stable"; no real use meets them yet.

A request that touches files now waits until jdtls has registered the files it watches (`Builds.awaitInitialized`,
called from `JdtlsLanguageServer.takeInChangesOnDisk`). Three things the review found are not settled.

Done when:
- A test shows what a request does when jdtls never registers its watchers (`FakeJdtls.holdsBackTheWatchers` never
  released, a short timeout): it ends with the timeout refusal and asks jdtls nothing (`prepareRename` is not received).
- The sentence of the help of `status` (`Operations`) says what the code waits for, and that the wait can end with a
  timeout; its "a few seconds" is backed by a measurement or dropped.
- Either the default registration of `FakeJdtls` (`**/*.java`) covers the build files a real jdtls watches, with the
  test that needs it, or the resolution says why no test needs it.
