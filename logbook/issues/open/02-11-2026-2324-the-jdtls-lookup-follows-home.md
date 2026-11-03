# The jdtls lookup follows HOME and is tested on every place it looks

Opened: 02-11-2026 23:24
On hold: findings of the reviews of `the-daemon-finds-the-pinned-jdtls-by-itself` and `mise-installs-a-pinned-gabier`,
no real use broken yet.

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
- The lookup of the jdtls shipped with the daemon (`JdtlsLocation`, added for mise) takes it only next to a jar that
  sits in a `lib/` folder. A jar at `/opt/app/gabier.jar` does not make it look in `/opt/jdtls`.
- A code source that is not a `file:` location (a `jar:` URL, a null location) falls through to the next place instead
  of throwing.
- The 2-argument `JdtlsLocation.launcher`, which `ProjectServer` calls, has a test that fails if it stops passing the
  distribution of the daemon.
- The refusal names the shipped place too, and `docs/daemon.md` lists the four places in the order they are tried.
