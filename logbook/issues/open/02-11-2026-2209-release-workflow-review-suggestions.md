# The release workflow gives its write token to one step and marks pre-releases by the semver rules

Opened: 02-11-2026 22:09
On hold: findings of the review of `a-tag-publishes-a-release-with-the-binaries`; they break no `Done when` point.

- `.github/workflows/release.yml:78`: `case "$VERSION" in *-*)` also matches a hyphen in build metadata, so tag
  `v1.0.0+build-5` (no pre-release part) is published with `--prerelease`.
- `.github/workflows/release.yml:45-66`: the `release` job, `contents: write`, runs the whole build (setup-build, mise,
  Gradle, the repository scripts) and gives `GITHUB_TOKEN` to `graalvm/setup-graalvm`; only `gh release create` needs
  the write scope.
- `.github/workflows/release.yml:2`: any tag `v*` is released, also one on a commit that is not on `main`.

Done when:
- `v1.0.0+build-5` gives a normal release and `v1.0.0-rc.1+b` a pre-release (the pre-release test reads the part before `+`);
- the build and the packaging run in a read-only job that hands `dist` over as an artifact, and a separate job with
  `contents: write` runs only `gh release create`;
- a tag on a commit that is not an ancestor of `main` fails before any build; `bin/ci` passes.
