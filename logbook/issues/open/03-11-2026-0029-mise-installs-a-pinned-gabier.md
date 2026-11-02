# mise installs a pinned gabier

Opened: 03-11-2026 00:29
Depends on: 03-11-2026-0029-a-release-installs-with-one-script.md

A Java project that uses mise pins gabier in its own `mise.toml`, like its other tools, with
`mise use github:NolwennD/gabier@<version>`. Each clone of that project then gets the same gabier. Today the cli is the
only thing mise installs this way: the mise github backend takes one release file per tool, and the daemon is a second
file. jdtls is installed by neither. Per platform, the release should hold one archive with `bin/gabier` and
`bin/gabier-daemon` next to each other, where the cli already looks. It should also say how jdtls gets installed, for
example through a `postinstall` of the mise tool, or with jdtls inside the archive. The ticket chooses between them and
says why in its resolution.

Done when:
- In a project whose `mise.toml` holds only `"github:NolwennD/gabier" = "<version>"` and a JDK 25, `mise install` gives
  a `gabier` whose `gabier diagnostics` answers, with no variable set.
- Changing the version in that `mise.toml` and running `mise install` switches the cli, the daemon and the jdtls they
  use together.
- The proof is made with a mise whose data folder is temporary, on a pre-release tag that the supervisor pushes with the
  go of the user.
- The README says how to pin gabier with mise.
