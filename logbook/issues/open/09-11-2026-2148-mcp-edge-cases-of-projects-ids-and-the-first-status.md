# The MCP server handles four edge cases of projects, ids and the first status

Opened: 09-11-2026 21:48
Findings of the review of the pull request of the issue `a-gabier-mcp-call-acts-on-the-project-of-its-file`, in the same
module (`gabier mcp`). What an agent can do once it is closed: use a project reached through a symlink, rely on the
answers of the server being one per id, and read a `status` right after the connection that is true.

Done when:
- a call for a file that does not exist yet, below a symlinked folder of its project, goes to the same daemon as a call for an existing file of that project (`SocketProjects.projectOf` resolves the nearest existing ancestor; shown by a test with a symlink to the project);
- the first calls for two different projects do not wait for each other's daemon launch (`DaemonLauncher.connect` locks per socket, not for the process);
- two requests with the same id never give two answers with that id: an immediate answer (`guide`, invalid values) for the id of a call that is still running is refused like any other duplicate, and the documentation says what the code does;
- `status` called right after the handshake, while the daemon that the server started for the launch project is not yet listening, does not answer `daemon-not-running`: it says that the daemon is starting (met in a real test of 0.5.1, 10-11-2026: the daemon was running when stopped afterwards, `status` had come too early); the documentation of `status` after the handshake (`mcp-instructions.md`, `docs/commands/mcp.md`) says what the code does, written from the code;
- `bin/ci` is green.
