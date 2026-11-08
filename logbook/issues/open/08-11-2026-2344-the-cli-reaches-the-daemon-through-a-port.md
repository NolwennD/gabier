# The cli reaches the daemon through a port

Opened: 08-11-2026 23:44

First step to a MCP facade (an MCP server as a second way in, next to the command line, calling the same operations).
By itself it gives an agent nothing new: it is structure, with identical behaviour. Today `Invocation` mixes the command
line (`Arguments`, `--json`, the output) with the way to the daemon (`ask`, `socket`, the launcher, the notice), so a
second front end would have to copy it. The daemon goes behind a port of the `eu.gabier.cli` context, which becomes the
hexagon that `HexagonalArchTest` already expects (`domain`, `application`, `infrastructure.primary`,
`infrastructure.secondary`; the layers are optional, which is why the flat cli passes today).

Done when:
- [ ] A port for the daemon (ask a method with named string parameters, get its result or a refusal) is in
  `eu.gabier.cli.domain`, in terms of the domain: no `eu.gabier.protocol` type and no JSON in it (`HexagonalArchTest` fails
  otherwise).
- [ ] `DaemonClient`, `DaemonLauncher`, `DaemonException` and `WaitNotice` are in `eu.gabier.cli.infrastructure.secondary`,
  and the socket adapter implements the port.
- [ ] `Invocation` holds the command line (arguments, `json`, output) and the port, and no longer opens the socket or
  starts the daemon itself; the commands run as before.
- [ ] `CliErrorKey` is split: the `daemon-*` keys leave with the adapter, the others stay; every key string is unchanged.
- [ ] An architecture test states that nothing of the cli outside `infrastructure.secondary` depends on it, except the
  class that wires the adapter (the composition root of the cli, named in the test with its reason).
- [ ] The tests of the cli pass with no change but their package and imports (no assertion edited), coverage stays at
  100%, `bin/ci` passes (the `native` stage included).
- [ ] The skills and docs that name these classes or this layout (`add-daemon-method`, `docs/cli.md`) say the new places.

Trap: the layered rule forbids any layer to reach the secondary adapters, so the adapter is built in a class outside the
layers (the root package of the context), and the commands receive the port through `Invocation`.
Trap: `DaemonLauncher` finds `gabier-daemon` next to its own executable (`ProcessHandle`): the move must not change how
it is found.
