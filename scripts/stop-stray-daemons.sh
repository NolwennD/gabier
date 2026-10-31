#!/bin/sh
# Stop the gabier daemons nobody uses any more: the project a daemon serves is a folder that no longer exists, or a
# worktree of .claude/worktrees that `git worktree list` does not know. A worker that is stopped, or whose worktree is
# removed, leaves its daemon (and the jdtls under it) running; the skills `ticket` and `ship` run this script.
# It looks at every socket in the folder of the default one and leaves alone a daemon whose project exists, the one of
# the main checkout and the ones of the workers in flight included.
set -u

command -v gabier >/dev/null 2>&1 || exit 0
command -v jq >/dev/null 2>&1 || { echo "jq is needed to read the status of a daemon" >&2; exit 1; }
cd "$(dirname "$0")/.." || exit 1

folder="${XDG_RUNTIME_DIR:+$XDG_RUNTIME_DIR/gabier}"
folder="${folder:-/tmp/gabier-$(id -u)}"

for socket in "$folder"/*.sock; do
  [ -S "$socket" ] || continue
  project=$(GABIER_SOCKET="$socket" gabier --json status 2>/dev/null | jq -r '.project // empty')
  [ -n "$project" ] || continue # nobody answers on this socket
  stray=false
  if [ ! -d "$project" ]; then
    stray=true
  else
    case "$project" in
      */.claude/worktrees/*)
        git worktree list --porcelain | grep -qxF "worktree $project" || stray=true
        ;;
    esac
  fi
  if [ "$stray" = true ]; then
    GABIER_SOCKET="$socket" gabier stop >/dev/null 2>&1
    echo "stopped the daemon of $socket, which served $project"
  fi
done
