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
