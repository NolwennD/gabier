# The release workflow builds and publishes the cli, the daemon and the archive of mise for macOS and Windows

Opened: 08-11-2026 17:15

For 0.5.0. Pushing a tag `v<version>` publishes, today, the Linux x86-64 assets only. The workflow builds one set of assets per
system, on a runner of that system (the native image does not cross-compile), and attaches them to one release:
`gabier-<os>-<arch>` (the cli), `gabier-daemon-<version>.tar.gz` (platform-independent, built once), the archive of mise per
system (`gabier-macos-aarch64.tar.gz`, `gabier-windows-x86_64.zip`, named so that the autodetection of the github backend of
mise picks the archive of the system: see `AGENTS.md`, "Releasing"), one `install.sh` that knows the system (macOS) and an
`install.ps1` (Windows), and one `SHA256SUMS` that lists them all. Inspiration: `bundle` then `publish` jobs of the release
workflow of `linkedin-archive-explorer` (a matrix of runners, an artifact per system, a last job that collects and runs
`gh release create`; a smoke test of the packaged binary on each system).

Done when:
- [ ] `release.yml` has a `bundle` job per system (Linux x86-64, macOS Apple Silicon, Windows x86-64; macOS Intel only if a
      free runner exists and the user asks) that builds with the version of the tag, packages with the script of the system
      (`scripts/package-release.sh` runs under `bash` on the Windows runner or has a counterpart: say which), smoke-tests the
      packaged binary (`--version`, `help`, `help agents`, `init-agent --dry-run` in an empty folder: nothing that needs jdtls), and
      uploads its assets; a last `publish` job collects them and creates the release once;
- [ ] a pre-release tag (`v0.5.0-rc.1`) gives a pre-release with all the assets; a system whose bundle fails fails the release
      (no release with a missing system), and the failed job says which;
- [ ] the notes (`.github/release-notes.md`), the README ("What is released so far", the install sections) and `AGENTS.md`
      ("Releasing") say which systems each release carries and how each is installed; `install.ps1` installs the cli, the
      daemon and jdtls, checks the sha256 and puts `gabier` on the user's `PATH`, as `install.sh` does;
- [ ] shown on a real pre-release tag pushed with the user's go (a tag needs it): the assets are listed, the sums check, and the
      result of each system's smoke test is quoted in the resolution; what is known not to work yet on a system (the daemon, jdtls,
      ...) is said in the release notes of that pre-release.

Depends on: the-ci-runs-the-build-and-tests-on-macos-and-windows (what the runners show decides what a release can carry).
