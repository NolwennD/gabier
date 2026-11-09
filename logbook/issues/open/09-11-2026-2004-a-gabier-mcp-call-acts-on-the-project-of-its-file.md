# A `gabier mcp` call acts on the project of the file it names

Opened: 09-11-2026 20:04
Depends on: mcp-keeps-answering-while-a-call-waits-and-reads-every-id

Real risk met while trying the server in a session whose working directory is not the one it was launched from (a
worktree entered after the host started, the usual use of this repository). `gabier mcp` takes its project from its own
working directory (`GABIER_PROJECT`, else the nearest folder above it with a `.git`) and builds one daemon port at start;
a relative `file` is made absolute from that same directory. Observed on 09-11-2026 with a server started in the main
checkout and a worktree inside it (`.claude/worktrees/<name>`):
- an absolute path in the worktree is refused (`language-server-file-outside-project`, a safe failure, but the agent
  cannot work in the worktree);
- a relative path is applied to the main checkout: `rename` with `dryRun` on `modules/cli/.../Gabier.java` listed files
  under the main checkout, not under the worktree where the agent is. Without `dryRun` it would have written there.

What an agent can do once it is closed and could not before: use the tools of the server in a worktree it entered after
the host launched, on the files of that worktree.

Done when:
- a call that names an absolute `file` (or `files`) goes to the daemon of the project of that file (the nearest folder
  above the file with a `.git`, else `GABIER_PROJECT`), one port per project built when first needed and kept, shown by a
  test with two project folders and two scripted daemons (a worktree has a `.git` file, not a folder);
- a relative path is refused as an invalid value that says to give an absolute path, because the server does not know
  the working directory of the session (this reverses the choice of the call issue, which made it absolute from the
  directory of the server: say so in the resolution); `mcp-instructions.md` and `docs/commands/mcp.md` say it;
- the files of one call that belong to two projects are refused;
- the daemon started at the handshake is that of the project of the launch directory, and a project reached later is
  started by its first call, with the wait said as in the waits issue;
- `bin/ci` is green.
