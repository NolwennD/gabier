# The cli reaches the daemon through a port

Opened: 08-11-2026 23:44

First step to a MCP facade (an MCP server as a second way in, next to the command line, calling the same operations).
By itself it gives an agent nothing new: it is structure, with identical behaviour. Today `Invocation` mixes the command
line (`Arguments`, `--json`, the output) with the way to the daemon (`ask`, `socket`, the launcher, the notice), so a
second front end would have to copy it. The daemon goes behind a port of the `eu.gabier.cli` context, which becomes the
hexagon that `HexagonalArchTest` already expects (`domain`, `application`, `infrastructure.primary`,
`infrastructure.secondary`; the layers are optional, which is why the flat cli passes today).

Done when:
- [x] A port for the daemon (ask a method with named string parameters, get its result or a refusal) is in
  `eu.gabier.cli.domain`, in terms of the domain: no `eu.gabier.protocol` type and no JSON in it (`HexagonalArchTest` fails
  otherwise).
- [x] `DaemonClient`, `DaemonLauncher`, `DaemonException` and `WaitNotice` are in `eu.gabier.cli.infrastructure.secondary`,
  and the socket adapter implements the port.
- [x] `Invocation` holds the command line (arguments, `json`, output) and the port, and no longer opens the socket or
  starts the daemon itself; the commands run as before.
- [x] `CliErrorKey` is split: the `daemon-*` keys leave with the adapter, the others stay; every key string is unchanged.
- [x] An architecture test states that nothing of the cli outside `infrastructure.secondary` depends on it, except the
  class that wires the adapter (the composition root of the cli, named in the test with its reason).
- [x] The tests of the cli pass with no change but their package and imports (no assertion edited), coverage stays at
  100%, `bin/ci` passes (the `native` stage included).
- [x] The skills and docs that name these classes or this layout (`add-daemon-method`, `docs/cli.md`) say the new places.

Trap: the layered rule forbids any layer to reach the secondary adapters, so the adapter is built in a class outside the
layers (the root package of the context), and the commands receive the port through `Invocation`.
Trap: `DaemonLauncher` finds `gabier-daemon` next to its own executable (`ProcessHandle`): the move must not change how
it is found.

## Resolution

Done in `d8e8ac8` (one commit, pull request #32). `bin/ci` of the worker, full, exit 0: format 19s, build 20s, test 286s
(Checkstyle, coverage 100%, architecture), release-install skipped (no changed file reaches `install.sh`), native 213s, jdtls
679s. The check `bin-ci` of the pull request passed (12m5s).

What was done:
- The port `Daemon` is in `eu.gabier.cli.domain`, with `DaemonReply`, `DaemonStart` and `DaemonRefusal`; no `protocol` type and no
  JSON in it. `SocketDaemon implements Daemon` is in `infrastructure.secondary` with `DaemonClient`, `DaemonLauncher`,
  `DaemonException` and `WaitNotice`. `Invocation` is `(arguments, json, out, daemon)`; `Gabier` builds the adapter.
- `CliErrorKey` is split: the `daemon-*` keys are in `DaemonErrorKey` (package-private), strings unchanged.
- `HexagonalArchTest.CliAdapters`: nothing of `eu.gabier.cli..` outside `..infrastructure.secondary..` but `eu.gabier.cli.Gabier`
  depends on the adapter package; a second test runs the rule without the exemption and expects a failure that names `Gabier`.
- `.claude/skills/add-daemon-method`, `.claude/skills/verify` and `docs/cli.md` ("How the cli is built") say the new places.

Choices, and why:
- **The trap of the issue was wrong.** It said a class outside the layers may build the adapter. ArchUnit's
  `shouldBeAnHexagonalArchitecture` counts that access as a violation of "secondary adapters may not be accessed by any layer".
  The existing rule is narrowed by `ignoreDependency` on the class `eu.gabier.cli.Gabier` alone (the constant
  `CLI_COMPOSITION_ROOT` carries the reason); the issue asked for such an exemption, named in the test with its reason.
- **`DaemonRefusal` is in the domain**, as a runtime exception with plain fields, because the port says "a result or a refusal" and a
  second front end can then catch it without naming the adapter. `SocketDaemon` turns a `protocol.Problem` into it and `Gabier`
  turns it back (`problemOf`). The cost is the six fields held in two types.
- **`statusAfter(Duration)` and `noticeWhileWaiting()` are methods of the port**, so that `Start` does not reach the adapter.
- "No assertion edited" is not met to the letter: 4 lines of `DaemonClientTest` read `DaemonErrorKey.DAEMON_CONNECTION_FAILED`
  instead of `CliErrorKey...`; the expected key string is the same. `public` was added on the test helpers `ScriptedDaemon`,
  `FakeDaemonScripts` and `CliFixtures`, which the moved tests now use from another package.

Review (neutral, once): nothing blocking. One suggestion, opened as an issue with an `On hold:` line (no `Done when` point and no
real use is broken): the category of a refusal crosses two hand-written copies and every refusal test uses `BAD_REQUEST`, so a copy
that fixed or swapped the category stays green (`DaemonAnswersTest`, `SocketDaemon.refusal`, `Gabier.problemOf`).

Against the dogfooding rule: the worker made some mechanical edits with `sed` and `python3` (the rename in `DaemonException.java` and
the moved tests, visibility, `Start.java`, the architecture test). Frictions met with gabier: `gabier move` leaves imports of the
package of the moved class (`DaemonClientTest`, `Start.java`), and there is no operation to move constants from one enum to another
(the split of `CliErrorKey` was a text edit). These become tickets.

What this opens: the steps 2 to 5 of the plan (pure renderings, the operations named once, one way to run them, the guide in the
domain), then the MCP adapter.

Tokens:
- worker: 196420 (`subagent_tokens`)
- reviewer: 90992 (`subagent_tokens`)
- supervisor: 28 calls, 19115 output tokens, 47858 input + cache write (64 + 47794), 4568693 cache read, over the period from the launch of the worker (22:44 UTC) to the merge; it covers the supervisor session, other work included (the plan discussion was before). Exchanges with the user: 0 messages (the filter counts 1, the expanded text of the `ship` skill, which the user did not type).
