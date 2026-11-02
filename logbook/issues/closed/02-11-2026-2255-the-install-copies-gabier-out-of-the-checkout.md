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

## Resolution

Fixed in `2c75dfa` (pull request #3). `scripts/install.sh` copies everything into `${GABIER_INSTALL_DIR:-${XDG_DATA_HOME:-~/.local/share}/gabier}/<version>`
and links `gabier` and `gabier-daemon` from the folder of the `PATH` to that copy. The copy holds the native cli, the
daemon distribution `daemon/{bin,lib}`, and `gabier-daemon`, a relative link. The cli takes the daemon next to the real
path of its own binary, so no Java code changed. The version folder comes from `gabier --version`, and a name that is
not plain stops the install. The copy is staged in `.<version>.new` and then moved into place. The refusal of a target
that is not a link now comes before the build.

Checked by the worker (`bin/ci`):
- format passed in 21s, build in 5s, test in 314s;
- native and jdtls were skipped, since no changed file reaches them;
- `bin-ci` on GitHub passed in 11m38s.

Each `Done when` point was proven on real runs in a temporary folder:
- after an install and `./gradlew clean`, `gabier --version`, `gabier diagnostics` and `gabier status` work;
- a second install replaces the first (a stale file is gone);
- a plain `gabier` file in the target is refused before the build and keeps its content;
- with a decoy `gabier-daemon` first on the `PATH`, the decoy never ran and the daemon came from the install's `lib`.

The supervisor ran the same checks under zsh 5.9 too, in a path with a space: first and second install, the refusal,
and the daemon of the install. The same pass over the other scripts found `stop-stray-daemons.sh` failing under zsh on a
glob that matches nothing, which pull request #4 fixes.

Review (once, no blocking finding). The five suggestions are small and go to a supervisor's follow-up:
- the comment says an interrupted install keeps the previous one, but a stop between `rm -rf` and `mv` leaves none (fix:
  move the old copy aside first);
- a failed copy leaves the staging folder, since there is no `trap`;
- the description of the `install` task in `mise.toml` still says link;
- a line of `.claude/rules/dogfooding.md` passes 120 columns;
- the install folder is created before the refusal check.

Not guarded, and noted by the worker: a second install of the same version deletes the copy that a running daemon still
uses, so that daemon must be restarted.

Tokens:
- worker: 100908
- reviewer: 59489
- supervisor: 64 calls, 43883 output, 93538 input + cache write (7864385 cache reads, apart). This covers the supervisor
  session from the launch of the two workers of this period, so the other ticket, the zsh pass and its pull request are
  included. Exchanges with the user: 1 message.
