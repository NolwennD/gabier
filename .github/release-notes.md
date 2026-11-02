gabier @VERSION@: Java refactorings for a coding agent, through a language server.

## What is attached

- `gabier-linux-x86_64`: the native `gabier` cli (Linux, x86-64).
- `gabier-daemon-@VERSION@.tar.gz`: the daemon `gabier-daemon` (its start script and its jars). It needs a JDK 25 or
  later (`JAVA_HOME`, else the `java` of the `PATH`).
- `SHA256SUMS`: the checksums of the two files above.

## Install

```sh
mkdir -p ~/.local/bin ~/.local/share/gabier
sha256sum --check --ignore-missing SHA256SUMS
install -m 755 gabier-linux-x86_64 ~/.local/bin/gabier
tar -C ~/.local/share/gabier -xzf gabier-daemon-@VERSION@.tar.gz
ln -sf ~/.local/share/gabier/gabier-daemon/bin/gabier-daemon ~/.local/bin/gabier-daemon
gabier --version
```

`~/.local/bin` must be in the `PATH`: the cli starts the `gabier-daemon` it finds next to itself, else on the `PATH`.

## jdtls is not bundled

gabier drives the Eclipse JDT language server, milestone @JDTLS_VERSION@. In a checkout of the tag `v@VERSION@`,
`mise run jdtls:install` installs it (checksum verified). By hand:

```sh
base=https://download.eclipse.org/jdtls/milestones/@JDTLS_VERSION@
archive=$(curl -fsSL "$base/latest.txt" | tr -d '[:space:]')
curl -fsSLO "$base/$archive"
echo "@JDTLS_SHA256@  $archive" | sha256sum --check
mkdir -p ~/.local/share/jdtls/@JDTLS_VERSION@
tar -xzf "$archive" -C ~/.local/share/jdtls/@JDTLS_VERSION@
```

The daemon finds it there by itself. `GABIER_JDTLS` can name another launcher. The launcher is a Python 3 script that
needs a JDK 21 or later.

## Use

Run `gabier` inside a Java project built with Gradle, Maven or Eclipse: `gabier help` lists the commands. The first
command starts the daemon of the project, which waits until jdtls has imported it. `gabier stop` ends it.
