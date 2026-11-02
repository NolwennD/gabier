#!/bin/sh
# Build the native cli and the daemon of this checkout, copy them into a folder of their own, and link `gabier` and
# `gabier-daemon` from a folder of the PATH to that copy, so that an agent calls them by name in any project. The copy
# keeps the two together: the cli starts the `gabier-daemon` next to itself (docs/daemon.md), so it never meets another
# one of the PATH. Nothing links into the checkout, which can be cleaned, or checked out at another version, without
# touching the install. A second install of a version replaces its copy; an existing link of the target folder is
# replaced; a file that is not a link is never touched.
# Usage: scripts/install.sh [<folder of the links> [<folder of the copies>]]
#   links:   $GABIER_BIN_DIR, else ~/.local/bin
#   copies:  $GABIER_INSTALL_DIR, else ${XDG_DATA_HOME:-~/.local/share}/gabier; the copy is <folder>/<version>/
set -eu

root=$(cd "$(dirname "$0")/.." && pwd)
folder=${1:-${GABIER_BIN_DIR:-$HOME/.local/bin}}
home=${2:-${GABIER_INSTALL_DIR:-${XDG_DATA_HOME:-$HOME/.local/share}/gabier}}
mkdir -p "$folder"
folder=$(cd "$folder" && pwd)

for name in gabier gabier-daemon; do
  if [ -e "$folder/$name" ] && [ ! -L "$folder/$name" ]; then
    echo "install: $folder/$name exists and is not a link: not replaced" >&2
    exit 1
  fi
done
mkdir -p "$home"
home=$(cd "$home" && pwd)

cd "$root"
cat <<EOF2
install: this will
  1. build the native cli (GraalVM native-image) and the daemon of $root,
  2. copy them into $home/<version>,
  3. link \`gabier\` and \`gabier-daemon\` from $folder to that copy.
The native image is the slow part: expect a few minutes (about 5, more while other builds run).
EOF2
./gradlew :cli:nativeCompile :daemon:installDist --console=plain

cli="$root/modules/cli/build/native/nativeCompile/gabier"
daemon="$root/modules/daemon/build/install/gabier-daemon"
for built in "$cli" "$daemon/bin/gabier-daemon"; do
  if [ ! -x "$built" ]; then
    echo "install: $built was not built" >&2
    exit 1
  fi
done

# "gabier <version>" is the first line of --version; a version that is not a plain name would escape the folder.
version=$("$cli" --version | head -n 1 | sed 's/^gabier //')
case $version in
  ''|*[!A-Za-z0-9._+-]*|.*)
    echo "install: cannot take a folder name from the version of the cli: '$version'" >&2
    exit 1
    ;;
esac

# The copy is made beside its place, the previous one is moved aside, and the new one moved in. Whatever stops the
# install (a failed copy, Ctrl-C) removes the staging folder and puts the previous copy back if it was moved aside.
target="$home/$version"
staging="$home/.$version.new"
previous="$home/.$version.old"
rm -rf "$staging" "$previous"
trap 'rm -rf "$staging"; if [ ! -e "$target" ] && [ -e "$previous" ]; then mv "$previous" "$target"; fi' EXIT
trap 'exit 130' INT TERM HUP
mkdir "$staging"
cp "$cli" "$staging/gabier"
cp -R "$daemon" "$staging/daemon"
ln -s daemon/bin/gabier-daemon "$staging/gabier-daemon"
if [ -e "$target" ]; then
  mv "$target" "$previous"
fi
mv "$staging" "$target"
rm -rf "$previous"
echo "copied gabier and gabier-daemon into $target"

for name in gabier gabier-daemon; do
  ln -sfn "$target/$name" "$folder/$name"
  echo "linked $folder/$name -> $target/$name"
done

case ":$PATH:" in
  *":$folder:"*) ;;
  *) echo "install: $folder is not in the PATH: add it to call gabier by name" >&2 ;;
esac
