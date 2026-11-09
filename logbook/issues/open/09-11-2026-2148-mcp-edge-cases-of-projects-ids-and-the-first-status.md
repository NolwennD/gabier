# The MCP server handles four edge cases of projects, ids and the first status

Opened: 09-11-2026 21:48
Depends on: 09-11-2026-2004-a-gabier-mcp-call-acts-on-the-project-of-its-file.md

Findings of the review of the pull request that closes the issue above, in the same module (`gabier mcp`). What an agent
can do once it is closed: use a project reached through a symlink, and rely on the answers of the server being one per id.

Done when:
- a call for a file that does not exist yet, below a symlinked folder of its project, goes to the same daemon as a call for an existing file of that project (`SocketProjects.projectOf` resolves the nearest existing ancestor; shown by a test with a symlink to the project);
- the first calls for two different projects do not wait for each other's daemon launch (`DaemonLauncher.connect` locks per socket, not for the process);
- two requests with the same id never give two answers with that id: an immediate answer (`guide`, invalid values) for the id of a call that is still running is refused like any other duplicate, and the documentation says what the code does;
- the documentation of `status` after the handshake (`mcp-instructions.md`, `docs/commands/mcp.md`) matches what the code does while the warm-up daemon is not listening yet (`daemon-not-running`), written from the code;
- `bin/ci` is green.
