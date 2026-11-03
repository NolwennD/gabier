# A release installs with one script

Opened: 03-11-2026 00:29

Today a user of gabier installs it from a release with twelve commands: the cli, the daemon, then jdtls. Each release
should attach an `install.sh` that does all of it in one command. The script carries the version of the release and
the jdtls milestone with the sha256 of its archive, which `scripts/package-release.sh` writes into it from the pins of
`mise.toml`. It refuses a file whose checksum does not match, before anything is installed.

Done when:
- `sh install.sh`, run in a folder that holds the files of a release, installs the cli, the daemon and the pinned jdtls.
  Then `gabier diagnostics` answers in a Gradle or Maven project, with no variable set.
- A file of the release with a wrong checksum is refused, and nothing is installed.
- The jdtls archive is checked against the sha256 that the script carries. An installed jdtls of the same milestone is
  left alone.
- The script runs under sh and zsh. The install folders can be changed in the same way as for `scripts/install.sh`.
- The release workflow attaches it and `SHA256SUMS` lists it.
- The README section "Use gabier on your project" gives this one command instead of the twelve.

## Resolution

Fixed in `7f3ca72` (pull request #7).
- `scripts/package-release.sh` fills `scripts/release-install.sh.template` into `install.sh` with the version and the
  jdtls pins of `mise.toml`, and refuses a placeholder left unfilled.
- `SHA256SUMS` lists the cli, the daemon archive and `install.sh`, and `release.yml` attaches the script.
- `install.sh` follows the layout of `scripts/install.sh`: the copy goes to `<GABIER_INSTALL_DIR>/<version>`, the links
  to `GABIER_BIN_DIR`, through a staging folder, a trap and a rollback. jdtls goes to `JDTLS_HOME`, else
  `~/.local/share/jdtls/<version>`.
- The script checks the cli and the daemon against `SHA256SUMS`, then the jdtls archive against the sha256 it carries,
  and only then writes anything.
- The README gives `sh install.sh` instead of the twelve commands.

Checked by the worker (`bin/ci`):
- Format and build passed.
- The test stage failed once on `MainTest...servesTheWorkingDirectoryByDefaultWithoutStartingJdtls` (a `ConnectException`,
  no Java touched). It passed alone: cli 598, core 693, daemon 884, protocol 383, shared 236, architecture 19.
- Native and jdtls skipped themselves.
- After the fix of the review, `bin/ci --fast` passed (format 13s, build 4s, test 203s).
- `bin-ci` on GitHub passed, in 11m49s.

The test that failed went to `03-11-2026-0051-a-daemon-test-fails-now-and-then.md`.

Each `Done when` point was proven on real runs, from a package of `0.1.0-rc.9`, under `env -i`, with a temporary
`HOME`, under sh and zsh:
- Install and use: the full install, with the real jdtls download, then `gabier diagnostics` answers in a Gradle
  project with no `GABIER_JDTLS`.
- Refusals, each with nothing installed: a tampered cli, a tampered daemon archive, a missing file, a tampered jdtls
  archive (served from `file://`), and a target that is not a link.
- A second run: jdtls is left alone (an unreachable download base, with the same inode and mtime).
- Folders: `GABIER_BIN_DIR`, `GABIER_INSTALL_DIR` and `JDTLS_HOME` were tested under zsh. The positional arguments were
  not tested, and the workflow was not run. The pre-release `v0.1.0-rc.2` of the next ticket will run it.

Review (once):
- BLOCKING, fixed by the worker: `rm -rf "$jdtls_home"` deleted whatever `JDTLS_HOME` named when it held no
  `bin/jdtls`. With `JDTLS_HOME=$HOME/.local/share/jdtls`, every installed milestone would be gone. Now only a missing
  folder, an empty folder or a broken jdtls install is replaced; any other folder is refused and left intact (proven
  with listings before and after, under sh and zsh).
- Suggestions, which go to a supervisor's follow-up:
  - the cli and the daemon archive are read again after the jdtls download, so they should be copied and checked as
    copies;
  - a `JDTLS_HOME` with a trailing slash fails with no `install:` message;
  - the two links are replaced one after the other;
  - two lines pass 120 columns (`package-release.sh`, `release.yml`);
  - `scripts/jdtls-install.sh` has the same `rm -rf` on a folder with no `bin/jdtls`, as the worker found.

Tokens:
- worker: 224279 (106846 + 117433, resumed once)
- reviewer: 64105
- supervisor: 23 calls, 13551 output, 36725 input + cache write (4578735 cache reads, apart). This covers the supervisor
  session from the launch of the worker; the issue of the daemon test that fails now and then is included. Exchanges
  with the user: 0 messages.
