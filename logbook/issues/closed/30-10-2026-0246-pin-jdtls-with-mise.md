# Pin jdtls with mise

Opened: 30-10-2026 02:46

Make `mise install` install one pinned version of jdtls, the Eclipse Java language server that gabier will drive, next to
hk and open-java-format in `mise.toml`. jdtls has no entry in the mise registry and no GitHub releases: it is published
as tarballs on download.eclipse.org, so find the least bad way (an `http` backend with a checksum, or a task) and say
why. The version is written once, so that a bot can bump it later; no Renovate configuration in this ticket.

Done when:
- in a fresh clone, `mise install` installs jdtls at the pinned version, and `mise exec -- jdtls --help` runs it (the
  `jdtls` launcher needs a JDK 21 or later: say which one it picks);
- the pinned version and its checksum, if any, are in `mise.toml` and nowhere else;
- the README says in one line how to get jdtls and which environment variable (`GABIER_JDTLS`, the path of the
  launcher) the probe tests will read.

## Resolution

Done in 165b35b, by a subagent, on the layout and the install script that the user supplied. `mise.toml` writes the
version once (`[vars] jdtls_version = "1.63.0"`, the latest milestone published on 2026-10-19). `JDTLS_HOME` is
`~/.local/share/jdtls/<version>`, outside the repository, so every worktree shares one install; mise exports
`GABIER_JDTLS` (the launcher) and puts `$JDTLS_HOME/bin` on the PATH. The task `jdtls:install` runs
`scripts/jdtls-install.sh`, and the `postinstall` hook chains it after `hk install --mise`, so `mise install` stays the
one setup command (`MISE_NO_HOOKS=1` turns both off).

The script installs a milestone after checking the sha256 that Eclipse publishes next to the archive; it is idempotent.
No checksum is written in `mise.toml`: the check detects a corrupted download, not an archive replaced on the server
itself, since both come from the same place. Writing the sha256 in `mise.toml` would be stricter, at the price of one
more edit per bump: to decide.

Checked by the subagent, `bin/ci`: format, build and test passed (`cli 9`, `protocol 4`, `architecture 11`), `native`
passed, `jdtls` skipped (no `jdtlsProbe` task yet). In a fresh clone `mise install` ran the install task, `mise exec --
jdtls --help` runs the launcher, a corrupted archive fails with the checksum message and installs nothing, a second run
is a no-op. The real `~/.local/share/jdtls` already held `1.63.0/` (a copy of the same tarball) and was not changed.

Neutral review, done once. What became of each finding:
- the final `mv` could move the staging directory inside an existing `JDTLS_HOME` without `bin/jdtls`, and the script
  then said "installed": it showed a `Done when` point was not met, sent back once and fixed (unique staging directory,
  `bin/jdtls` checked after the extraction, an invalid install removed first), checked by `bin/ci` and by proofs in a
  scratch directory;
- the README did not say which JDK the launcher picks: fixed, once;
- the checksum check passed when both sides were empty (an empty `.sha256`, no `sha256sum`): fixed in the same pass, it
  now requires 64 hexadecimal characters and falls back to `shasum -a 256`;
- the README line over the width and the need for `mise exec --` outside an activated shell: fixed in the same pass;
- fetching over https only, validating the name in `latest.txt`, the temporary directory left by a signal under dash,
  and the `JDTLS_DOWNLOAD_BASE` variable (the user's): left;
- "about a minute" in the `verify` skill comes from the supervisor's conflict resolution with the native cli ticket.

To remember: the launcher needs Python 3 and a JDK 21 or later (`$JAVA_HOME/bin/java`, else `java` on the PATH); a new
milestone needs its `latest.txt` and `.sha256` on download.eclipse.org; `JDTLS_HOME` and `GABIER_JDTLS` leak into every
process run by mise in this repository, and `jdtls` shadows another install on the PATH there.
