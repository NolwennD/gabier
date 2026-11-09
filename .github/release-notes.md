gabier @VERSION@: Java refactorings for a coding agent, through a language server.

@WHAT_CHANGED@

## Attached

- `gabier-linux-x86_64`: the native `gabier` cli (Linux, x86-64).
- `gabier-daemon-@VERSION@.tar.gz`: the daemon `gabier-daemon` (its start script and its jars), which needs a JDK 25
  or later.
- `gabier-linux-x86_64.tar.gz`: what `mise use github:NolwennD/gabier@@VERSION@` installs: `bin/gabier`,
  `bin/gabier-daemon`, `lib/` and the `jdtls/` below, side by side.
- `install.sh`: installs the cli, the daemon and jdtls, in one command. It carries the version of this release and the
  jdtls pins below.
- `SHA256SUMS`: the checksums of the four files above.

## Requires

- A JDK 25 or later, and Python 3 for the launcher of jdtls.
- jdtls @JDTLS_VERSION@, the milestone this release is tested against, not attached. The sha256 of its archive is
  `@JDTLS_SHA256@`.

With mise, pin the version in the `mise.toml` of your project: `mise use github:NolwennD/gabier@@VERSION@`. With no
mise, download the cli, the daemon, `install.sh` and `SHA256SUMS` into one folder and run `sh install.sh`: it checks the
files, installs gabier and jdtls, and links `gabier` and `gabier-daemon` from `~/.local/bin`. The details are in the
[README](https://github.com/NolwennD/gabier/blob/v@VERSION@/README.md#use-gabier-on-your-project).
