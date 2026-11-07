# The message at the end of install.sh quotes the paths it prints, and its stage covers more cases (review suggestions)

Opened: 07-11-2026 15:48
On hold: findings of the review of `github-page-and-install-next-steps`, none breaks a `Done when` point; the first one is
a real use only for an install folder that holds a space.

- `scripts/release-install.sh.template:196` and `:201`: `$gabier` is printed unquoted, so with links in
  `/home/me/my tools/bin`, off the `PATH`, the line `/home/me/my tools/bin/gabier --version` cannot be pasted; the
  `cd <your Java project> && …` line breaks the same way.
- `bin/ci.d/35-release-install:85`: only two cases (links folder on the `PATH` or not). Missing: a folder with a space,
  and a failure before the end (a corrupted `SHA256SUMS` gives a non-zero exit and no `is installed` on stdout).
- `bin/ci.d/35-release-install:146`: `writeRelease` fills the three placeholders by hand and never checks that none is
  left; a new `@X@` in the template passes this stage and fails only in `package-release.sh` (which checks `@[A-Z_]*@` at
  `package-release.sh:90`).
- `bin/ci.d/35-release-install:35`: `reachedBy` lists the template and the stage only; a change to
  `scripts/package-release.sh`, which fills the template, does not trigger it.
- `bin/ci.d/35-release-install:25`: the `MESSAGE` of the test repeats the agent line of the README and of the template;
  nothing ties the README line to the script.

Done when:
- [ ] the printed paths are quoted when they hold a space, and a test shows it;
- [ ] the stage covers the failure case and a leftover placeholder, and runs when `package-release.sh` changes;
- [ ] the agent line has one source (the template), and the README is checked against it;
- [ ] `bin/ci` passes.
