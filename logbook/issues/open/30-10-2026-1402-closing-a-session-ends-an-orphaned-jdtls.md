# Closing a jdtls session ends jdtls when its launcher died first

Opened: 30-10-2026 14:02

`JdtlsSession.close()` reads the process tree (`process.descendants()`) only when it runs. If the launcher died
before, the tree is empty: the java child of jdtls keeps running and the data folder is deleted under it.

Done when:
- [ ] A test starts a stand-in launcher with a child (`sh -c "sleep 300 & wait"`), kills the launcher, calls `close()`
      and asserts the child is dead and the data folder gone (red first for this reason).
- [ ] The descendants are recorded once the session is ready and killed with the current ones at close.
- [ ] `bin/ci` green, 100% coverage, no exclusion.
