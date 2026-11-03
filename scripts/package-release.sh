#!/bin/sh
# Package the release assets of a version, from the build of this checkout, into a folder: the native cli, the archive of
# the daemon (the folder `installDist` makes, `gabier-daemon/bin` and `gabier-daemon/lib`), the archive of mise
# (`gabier-linux-x86_64.tar.gz`: `bin/gabier`, `bin/gabier-daemon`, `lib/` and the pinned `jdtls/`, see below),
# `install.sh` (written from scripts/release-install.sh.template), `SHA256SUMS` (which lists the four), and the release
# notes. It first checks that the two programs print the version they were built with: a release that says another
# version than its tag is refused.
#
# The archive of mise is what `mise use github:NolwennD/gabier@<version>` installs: mise puts its `bin/` on the PATH, the
# cli starts the `gabier-daemon` next to it, and the daemon finds the `jdtls/` folder next to its `lib/` (JdtlsLocation).
# jdtls is downloaded from Eclipse by scripts/jdtls-install.sh, checked against the sha256 pinned in mise.toml
# (JDTLS_DOWNLOAD_BASE moves the download, for a test). Its name carries no version, so that the autodetection of mise
# picks it over the bare cli `gabier-linux-x86_64` (same name, +10 for an archive): the tag gives the version.
#
# The build is made with the same version beforehand (what .github/workflows/release.yml does):
#   ./gradlew :cli:nativeCompile :daemon:installDist -PgabierVersion=<version>
# Usage: scripts/package-release.sh <version> <output folder>    (the version is the tag without its `v`: 0.1.0)
set -eu

version=${1:?usage: package-release.sh <version> <output folder>}
out=${2:?usage: package-release.sh <version> <output folder>}

root=$(cd "$(dirname "$0")/.." && pwd)
cli="$root/modules/cli/build/native/nativeCompile/gabier"
install="$root/modules/daemon/build/install"
daemon="$install/gabier-daemon/bin/gabier-daemon"

if ! printf '%s' "$version" | grep -Eq '^(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)(-[0-9A-Za-z.-]+)?(\+[0-9A-Za-z.-]+)?$'; then
  echo "package-release: '$version' is not a semantic version (the tag is v<version>)" >&2
  exit 1
fi
for built in "$cli" "$daemon"; do
  if [ ! -x "$built" ]; then
    echo "package-release: $built was not built" >&2
    exit 1
  fi
done

printed=$("$cli" --version)
if [ "$printed" != "gabier $version" ]; then
  echo "package-release: the cli prints '$printed', not 'gabier $version': it was built with another version" >&2
  exit 1
fi
printed=$("$daemon" --version)
if [ "$printed" != "gabier-daemon $version" ]; then
  echo "package-release: the daemon prints '$printed', not 'gabier-daemon $version': it was built with another version" >&2
  exit 1
fi

mkdir -p "$out"
out=$(cd "$out" && pwd)
cli_asset=gabier-linux-x86_64
daemon_asset=gabier-daemon-$version.tar.gz
mise_asset=gabier-linux-x86_64.tar.gz
rm -f "$out/$cli_asset" "$out/$daemon_asset" "$out/$mise_asset" "$out/install.sh" "$out/SHA256SUMS" "$out/release-notes.md"

cp "$cli" "$out/$cli_asset"
chmod 755 "$out/$cli_asset"
tar -C "$install" -czf "$out/$daemon_asset" gabier-daemon
# The jdtls that gabier is built and tested against: the pins of mise.toml, which the notes quote and install.sh carries.
jdtls_version=$(sed -n 's/^jdtls_version = "\(.*\)"$/\1/p' "$root/mise.toml")
jdtls_sha256=$(sed -n 's/^jdtls_sha256 = "\(.*\)"$/\1/p' "$root/mise.toml")
if [ -z "$jdtls_version" ] || [ -z "$jdtls_sha256" ]; then
  echo "package-release: mise.toml does not pin jdtls (jdtls_version, jdtls_sha256)" >&2
  exit 1
fi
# The archive of mise: one folder with bin/, lib/ and jdtls/.
layout=$(mktemp -d)
trap 'rm -rf "$layout"' EXIT
mkdir "$layout/bin"
cp "$cli" "$layout/bin/gabier"
cp "$daemon" "$layout/bin/gabier-daemon"
cp -R "$install/gabier-daemon/lib" "$layout/lib"
JDTLS_HOME="$layout/jdtls" sh "$root/scripts/jdtls-install.sh" "$jdtls_version" "$jdtls_sha256"
for launcher in bin/gabier bin/gabier-daemon jdtls/bin/jdtls; do
  if [ ! -x "$layout/$launcher" ]; then
    echo "package-release: $launcher is not executable in the archive of mise" >&2
    exit 1
  fi
done
tar -C "$layout" -czf "$out/$mise_asset" bin lib jdtls
fill() {
  sed -e "s|@VERSION@|$version|g" \
      -e "s|@JDTLS_VERSION@|$jdtls_version|g" \
      -e "s|@JDTLS_SHA256@|$jdtls_sha256|g" \
      "$1" > "$2"
}
fill "$root/scripts/release-install.sh.template" "$out/install.sh"
if grep -q '@[A-Z_]*@' "$out/install.sh"; then
  echo "package-release: install.sh still holds a placeholder: scripts/release-install.sh.template and this script disagree" >&2
  exit 1
fi
chmod 755 "$out/install.sh"
fill "$root/.github/release-notes.md" "$out/release-notes.md"
(cd "$out" && sha256sum "$cli_asset" "$daemon_asset" "$mise_asset" install.sh > SHA256SUMS)

echo "package-release: gabier $version packaged in $out"
(cd "$out" && ls -l "$cli_asset" "$daemon_asset" "$mise_asset" install.sh SHA256SUMS release-notes.md)
