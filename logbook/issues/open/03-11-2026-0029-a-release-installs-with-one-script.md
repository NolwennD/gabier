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
