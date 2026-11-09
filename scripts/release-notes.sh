#!/bin/sh
# Write the release notes of a version on the standard output: the template, with the line `@WHAT_CHANGED@` replaced by
# the file that says what changed in the commands for that version, `docs/releases/<version>.md` (the folder is
# RELEASE_NOTES_FOLDER for a test). The file is written before the tag is pushed, by whoever prepares the release; the
# version is the tag without its `v`, whole (`0.6.0-rc.1` has a file of its own, `docs/releases/0.6.0-rc.1.md`).
#
# It fails, writing nothing, when that file is missing or holds only blanks, with a message that names it, and when the
# template has no `@WHAT_CHANGED@` line. The release workflow runs it before anything is built (so a tag whose version
# has no file publishes nothing), and scripts/package-release.sh runs it to make `release-notes.md`. It only ever looks
# at the version it is given: a version released before this rule has no file and is not asked for one.
# Usage: scripts/release-notes.sh <version> <template>
set -eu

version=${1:?usage: release-notes.sh <version> <template>}
template=${2:?usage: release-notes.sh <version> <template>}

root=$(cd "$(dirname "$0")/.." && pwd)
folder=${RELEASE_NOTES_FOLDER:-$root/docs/releases}
file=$folder/$version.md
shown=${file#"$root"/}

if [ ! -f "$file" ]; then
  echo "release-notes: $shown is missing: write what changed in the commands for $version (a section 'New in $version') before the tag v$version" >&2
  exit 1
fi
if ! grep -q '[^[:space:]]' "$file"; then
  echo "release-notes: $shown is blank: write what changed in the commands for $version before the tag v$version" >&2
  exit 1
fi
if ! grep -qx '@WHAT_CHANGED@' "$template"; then
  echo "release-notes: $template has no line '@WHAT_CHANGED@' to put $shown in" >&2
  exit 1
fi

# The file is read as it is, with no substitution: the template is filled before this runs.
awk -v file="$file" '
  $0 == "@WHAT_CHANGED@" {
    while ((getline line < file) > 0) print line
    close(file)
    next
  }
  { print }
' "$template"
