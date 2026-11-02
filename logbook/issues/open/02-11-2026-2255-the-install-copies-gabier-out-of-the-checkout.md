# The install copies gabier out of the checkout

Opened: 02-11-2026 22:55

Someone who installs gabier from the sources keeps a working `gabier` after `./gradlew clean`, or after checking out
another version in the clone. Today `scripts/install.sh` (`mise run install`) links `gabier` and `gabier-daemon` to
files of the build in the checkout, so a clean breaks the install, and a checkout of another version followed by a
build changes it without a new install. The install should copy the native cli and the distribution of the daemon into
a folder of their own, for example `~/.local/share/gabier/<version>`. It should keep them together, so that the cli
finds the daemon next to itself, and link the commands from the folder of the `PATH` to that copy.

Done when:
- After `mise run install` then `./gradlew clean`, `gabier --version` and a request still work.
- A second install replaces the previous one. It never touches a file of the target folder that is not a link it made
  (as today).
- The cli that is run starts the daemon of the same install, not another `gabier-daemon` of the `PATH`.
- The README, AGENTS.md and `docs/daemon.md` describe the install as a copy, and say where it goes and how to change that.
