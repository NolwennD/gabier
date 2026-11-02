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

## Resolution

Done in `64066c0`. A push of a tag `v*` runs `.github/workflows/release.yml`: job `version` (the tag must be `v<semver>`,
else it fails), job `check` (`ci.yml` as a reusable workflow: the full `bin/ci --all` on the tagged commit), job `release`
(needs both, the only one with `contents: write`): the native cli and `installDist` built with
`-PgabierVersion=<version>`, `scripts/package-release.sh` (refuses a cli or daemon whose `--version` is not the tag's; writes
`gabier-linux-x86_64`, `gabier-daemon-<version>.tar.gz`, `SHA256SUMS` and the notes, which give the pinned jdtls version and
sha256 read from `mise.toml`), then `gh release create --verify-tag`, with `--prerelease` when the version has a `-`
(decision of the user, 02-11-2026: the first tag is `v0.1.0-rc.1`). The install steps moved to the composite action
`.github/actions/setup-build`, which `ci.yml` and `release.yml` share. The version flows from the tag to
`version.properties` of `modules/protocol` (`processResources`), read by `ProtocolVersion.CURRENT`, now
`record ProtocolVersion(String value)`, a checked semver: `gabier --version`, `gabier-daemon --version` and `gabier status`
print it; a build with no version is `0.0.0-dev`. `README.md` gains "Install from a release" and "Releasing".

Checked: the worker's `bin/ci`, not in one run: format, build, test (cli 598, core 693, daemon 866, protocol 383, shared
236, architecture 19, 0 failures) and jdtls passed; native failed once (a `0.1` left in `bin/ci.d/40-native`) and passed
alone after the fix. Then the check `bin-ci` of pull request #2 on `64066c0`: format 6s, build 76s, test 125s, native 77s,
jdtls 219s, all passed. Locally, native builds at `0.1.0` and `0.1.0-rc.1` packaged by the script: `sha256sum --check` OK,
the cli prints `gabier 0.1.0` / `gabier 0.1.0-rc.1`, the daemon of the archive prints its version. `actionlint` was not run
(its install was refused to the worker). The GitHub release itself is proven by the first tag, `v0.1.0-rc.1`, after this
close.

Reviewed once: nothing blocking. Suggestions, opened on hold: `release-workflow-review-suggestions` (a `-` in build
metadata marks a pre-release; the write token reaches the whole build; a tag off `main` is released) and
`the-release-version-is-named-for-what-it-is` (`ProtocolVersion` holds the release version; its test of the pass-through
cannot fail on a default build).

Tokens:
- worker: 174 836 (stopped by the supervisor after its report, which it kept sending again)
- reviewer: 67 804
- supervisor: 62 calls, 28 179 output, 95 775 input + cache write (21 488 480 cache read apart); the supervisor session from the launch of the worker to the close, the other work of the period included (the protection of main and of the tags, the rules); exchanges with the user: 5 messages (a floor)
