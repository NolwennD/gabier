# A command runs an operation through one entry point

Opened: 09-11-2026 07:55
Depends on: each-operation-names-its-parameters-once

Step 4 of the plan that prepares the cli for an MCP facade (structure only). Each command reads its words, builds the
parameter map, asks the daemon and decodes the answer. The application gets one entry point, `run(operation, named
values)`, that a second primary adapter calls too.

Done when:
- the application layer has `run(operation, named values)`: it checks the values against the descriptor (a required
  one missing, an unknown one), converts the positions from 1 to the daemon's 0, asks the `Daemon` port and returns the
  result decoded into its `protocol` type;
- every command that asks the daemon (`diagnostics`, `rename` both forms, `organize-imports`, `cleanup`, the four
  `extract-*`, `move-type-to-file`, `move`, `inline`, `create`, `references`, `actions`, `apply-action`) is
  reduced to "words to named values" and "result to output"; none builds a `Map.of(...)` of daemon parameters;
- started with `diagnostics`, `rename` and `extract-method`, to prove the shape before the others;
- the cli tests pass unchanged apart from their package; the refusal a command shows is the same as before;
- the resolution says whether the six-field `DaemonRefusal` is kept or replaced, and why;
- `bin/ci` is green.
