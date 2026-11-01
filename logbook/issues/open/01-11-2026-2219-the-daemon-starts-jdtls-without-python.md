# The daemon starts jdtls without Python

Opened: 01-11-2026 22:19
On hold: the user does not want it started (01-11-2026); lifted by the user.

An agent or a person installs gabier on a machine with a JDK and no Python, and `gabier` works: the daemon starts the
Equinox launcher of jdtls itself, instead of running `bin/jdtls`, a Python 3 script of the Eclipse archive that does
nothing more than build a `java` command line (decision of the user, 01-11-2026: total replacement, no fallback).
Today `JdtlsLauncher` runs `<GABIER_JDTLS> -data <dir>`.

**Care**: jdtls is configured, not fought: start from the `jdtls.py` of the pinned version
(`org.eclipse.jdt.ls.product/scripts/jdtls.py` of the jdtls source: the user clones it, `.claude/rules/workflow.md`,
"Orientation") and copy only what it does for a daemon that passes its own `-data`: the `java` of the daemon itself (never
a `java` of the `PATH`), the JVM options (`-Xms1G`, `--add-modules=ALL-SYSTEM`, the two `--add-opens`, and the two
`-Djdk.xml...` limits from Java 24), `-jar plugins/org.eclipse.equinox.launcher_*.jar`, the shared configuration of the OS
(`config_linux`, `config_mac`, `config_win`). Its history (21 commits in 4 years, the JVM options changed once) says the
copy is cheap to keep. The daemon needs the installation folder of jdtls, not only the launcher: say in the issue's
resolution how it is found (`GABIER_JDTLS` still names `bin/jdtls`, or a variable for the folder) and why.

Done when:
- [ ] The daemon starts jdtls with no `python3` on the `PATH`, shown by the `jdtls` stage of `bin/ci` run with
      `python3` hidden, and by an integration test that fails if the command line changes (the options above, one by
      one).
- [ ] The command line is built in the adapter only; the core and the cli do not change. A missing or malformed
      installation (no Equinox launcher, an OS with no shared configuration) is a refusal that says which.
- [ ] No file of the repository, rule or doc says Python is needed: `AGENTS.md` and `mise.toml` are updated, and
      `scripts/jdtls-install.sh` still installs the archive (the Python files in it are left alone, not run).
- [ ] `bin/ci` passes.
