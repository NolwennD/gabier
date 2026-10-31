#!/bin/sh
# Build the native cli and the daemon of this checkout, and link `gabier` and `gabier-daemon` into a folder of the PATH,
# so that an agent calls them by name in any project. The cli starts the daemon it finds next to itself, else on the PATH
# (docs/daemon.md). The links point into this checkout: run it from the checkout the tools should come from, and
# again after a change. An existing link is replaced; a file that is not a link is never touched.
# Usage: scripts/install.sh [<folder>]    (default: $GABIER_BIN_DIR, else ~/.local/bin)
set -eu

root=$(cd "$(dirname "$0")/.." && pwd)
folder=${1:-${GABIER_BIN_DIR:-$HOME/.local/bin}}
mkdir -p "$folder"
folder=$(cd "$folder" && pwd)

cd "$root"
cat <<EOF
install: this will
  1. build the native cli (GraalVM native-image) and the daemon of $root,
  2. link \`gabier\` and \`gabier-daemon\` into $folder.
The native image is the slow part: expect a few minutes (about 5, more while other builds run).
EOF
./gradlew :cli:nativeCompile :daemon:installDist --console=plain

cli="$root/modules/cli/build/native/nativeCompile/gabier"
daemon="$root/modules/daemon/build/install/gabier-daemon/bin/gabier-daemon"
for built in "$cli" "$daemon"; do
  if [ ! -x "$built" ]; then
    echo "install: $built was not built" >&2
    exit 1
  fi
done

for name in gabier gabier-daemon; do
  if [ -e "$folder/$name" ] && [ ! -L "$folder/$name" ]; then
    echo "install: $folder/$name exists and is not a link: not replaced" >&2
    exit 1
  fi
done
for pair in "gabier:$cli" "gabier-daemon:$daemon"; do
  name=${pair%%:*}
  target=${pair#*:}
  ln -sfn "$target" "$folder/$name"
  echo "linked $folder/$name -> $target"
done

case ":$PATH:" in
  *":$folder:"*) ;;
  *) echo "install: $folder is not in the PATH: add it to call gabier by name" >&2 ;;
esac
