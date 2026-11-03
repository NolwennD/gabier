#!/bin/sh
# Install a pinned jdtls milestone into $JDTLS_HOME, after checking the archive against the sha256 pinned in mise.toml.
# The checksum comes from this repository, not from the server that serves the archive: a replaced archive is refused.
# Idempotent: an installed version is left alone.
# Usage: JDTLS_HOME=<dir> scripts/jdtls-install.sh <version> <sha256>
set -eu

version=${1:?usage: jdtls-install.sh <version> <sha256>}
expected=$(printf '%s' "${2:?usage: jdtls-install.sh <version> <sha256>}" | tr 'A-F' 'a-f')
home=${JDTLS_HOME:?JDTLS_HOME is not set}
base=${JDTLS_DOWNLOAD_BASE:-https://download.eclipse.org/jdtls/milestones}

if [ -x "$home/bin/jdtls" ]; then
  echo "jdtls $version is already installed in $home"
  exit 0
fi

# JDTLS_HOME may name any folder: one that is not a usable jdtls is replaced only when it is missing, empty, or a broken
# install (it holds a bin/jdtls that cannot be run). Anything else is refused, never deleted.
if [ -e "$home" ]; then
  if [ ! -d "$home" ] || { [ ! -e "$home/bin/jdtls" ] && [ -n "$(ls -A "$home")" ]; }; then
    echo "jdtls $version: $home exists and is not a jdtls install (no bin/jdtls) nor empty: not replaced" >&2
    exit 1
  fi
fi

if ! printf '%s' "$expected" | grep -Eq '^[0-9a-f]{64}$'; then
  echo "jdtls $version: the pinned checksum is not a sha256 (got '$expected')" >&2
  exit 1
fi

work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT

# latest.txt names the archive of the milestone (it carries a build timestamp)
curl -fsSL -o "$work/latest.txt" "$base/$version/latest.txt"
archive=$(tr -d '[:space:]' <"$work/latest.txt")
curl -fsSL -o "$work/$archive" "$base/$version/$archive"
if command -v sha256sum >/dev/null 2>&1; then
  actual=$(sha256sum "$work/$archive" | awk '{print $1}')
else
  actual=$(shasum -a 256 "$work/$archive" | awk '{print $1}')
fi
if [ -z "$actual" ] || [ "$expected" != "$actual" ]; then
  echo "jdtls $version: checksum mismatch for $archive (expected '$expected', got '$actual')" >&2
  exit 1
fi

mkdir -p "$(dirname "$home")"
staging=$(mktemp -d "$home.XXXXXX")
trap 'rm -rf "$work" "$staging"' EXIT
tar -xzf "$work/$archive" -C "$staging"
if [ ! -x "$staging/bin/jdtls" ]; then
  echo "jdtls $version: $archive holds no bin/jdtls launcher" >&2
  exit 1
fi
chmod 755 "$staging"
if [ -x "$home/bin/jdtls" ]; then
  echo "jdtls $version is already installed in $home"
  exit 0
fi
# an invalid install (no launcher) is replaced, so that mv never moves the staging directory inside it
rm -rf "$home"
mv "$staging" "$home"
echo "jdtls $version installed in $home"
