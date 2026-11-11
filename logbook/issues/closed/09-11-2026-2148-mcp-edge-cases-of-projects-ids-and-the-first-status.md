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

## Resolution

Commit 955174e (pull request 42, merged fast-forward). `bin/ci` of the worker, full run on the last commit: format passed, build passed, test passed (cli 1554, core 727, daemon 1022, protocol 414, shared 236, architecture 22; coverage 100%), release-install and release-notes skipped (no changed file reaches them), native passed, jdtls passed; coupling ceilings unchanged (159 scenarios). Check of pull request 42 (`bin-ci`): pass.

- A file that does not exist yet below a symlinked folder goes to the same daemon as an existing one (`SocketProjectsTest`; red with the old code); the launch lock of `DaemonLauncher` is per socket (`DaemonLauncherTest`, red with the old global lock); a request whose id is that of a running call is refused with -32600 and a null id, so no two answers carry one id (`WaitingCallsTest`); `status` right after the handshake answers the new key `daemon-starting` while the warm-up runs (`McpServerTest.WarmingUpTheDaemon`, `WarmUpTest`).
- Behaviour changes, said in `docs/commands/mcp.md` and `mcp-instructions.md`: the refusal of a duplicate id carries a null id (it carried the duplicate's id); `daemon-starting` is a new key of `status`.
- The worker also fixed a flake of `WaitingCallsTest` (`daemon.asked()` read before the call had asked), in a test it rewrote.
- Review: no blocking finding, no suggestion on this ticket.

Tokens:
- worker: 315858 (one worker for the batch of three issues, one commit each)
- reviewer: 145126 (one review of the batch)
- supervisor: 30 calls, output 13576, input + cache write 164895 (cache reads 2684070), over the period of the batch (launch of the worker 10-11-2026 23:46 UTC to the merge), other work included; exchanges with the user: 4 messages
