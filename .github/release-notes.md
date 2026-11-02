gabier @VERSION@: Java refactorings for a coding agent, through a language server.

## Attached

- `gabier-linux-x86_64`: the native `gabier` cli (Linux, x86-64).
- `gabier-daemon-@VERSION@.tar.gz`: the daemon `gabier-daemon` (its start script and its jars), which needs a JDK 25
  or later.
- `SHA256SUMS`: the checksums of the two files above.

## Requires

- A JDK 25 or later, and Python 3 for the launcher of jdtls.
- jdtls @JDTLS_VERSION@, the milestone this release is tested against, not attached. The sha256 of its archive is
  `@JDTLS_SHA256@`.

The install, jdtls included, is in the
[README](https://github.com/NolwennD/gabier/blob/v@VERSION@/README.md#use-gabier-on-your-project).
