# The jdtls lookup follows HOME, and its PATH test can fail

Opened: 02-11-2026 23:24
On hold: findings of the review of `the-daemon-finds-the-pinned-jdtls-by-itself`, no real use broken yet.

The daemon looks for the pinned jdtls under `user.home` (`modules/daemon/src/main/java/eu/gabier/daemon/Main.java`),
which the JVM takes from the passwd entry on Linux. mise and `scripts/jdtls-install.sh` install it under `$HOME`. So
`HOME=/tmp/h mise run jdtls:install` followed by a daemon started with the same `HOME` ends in `no jdtls found`.
Separately, `JdtlsLocationTest.Finding.skipsTheEmptyEntriesOfThePath` cannot fail: `Pattern.split(":")` gives no
entry, so removing the `!entry.isEmpty()` check of `JdtlsLocation` leaves it green.

Done when:
- A daemon whose `HOME` differs from the passwd home finds the pinned jdtls under `$HOME/.local/share/jdtls/<version>`.
  Without `HOME`, it falls back to `user.home`.
- The empty-entry test fails when the empty-entry check is removed, for example with a `PATH` holding an empty entry
  and a `jdtls` in the working directory.
