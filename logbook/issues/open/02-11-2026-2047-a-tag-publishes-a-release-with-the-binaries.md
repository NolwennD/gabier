# A tag publishes a GitHub release with the binaries and the jars

Opened: 02-11-2026 20:47

Someone who does not build gabier can install it: pushing a tag `v<version>` on `github.com/NolwennD/gabier` (decision
of the user, 02-11-2026) builds and attaches to a GitHub release of that tag the native `gabier` cli for Linux x86-64 and
the distribution of the daemon (`gabier-daemon`: its start script and its jars, as `installDist` lays them out), with a
checksum file. jdtls is not bundled: the release notes say how to install the pinned one. The version that `gabier
--version` and `gabier status` print comes from the tag, not from a constant written by hand.

Done when:
- a tag `v0.1.0` (a test tag on a fork or a dry run of the workflow) gives a release holding the cli, the daemon
  archive and `SHA256SUMS`, and the cli downloaded from it runs `gabier --version` and answers `0.1.0`;
- the release is built only from a commit on which the full `bin/ci` passed, by the same setup as the check of the pull
  requests;
- `README.md` says how to install from a release; `bin/ci` passes.

Depends on: github-runs-the-full-bin-ci-on-every-pull-request.
