# A cli command is declared in its own class, with a step builder (structure only)

Opened: 01-11-2026 11:00

`Command.java` is an enum that holds the name, summary, usage, example and refusals of every command: about 210 lines for 9
commands, one more with each command, and each constant passes five positional arguments. A new piece of data (an alias, an
option, a "since") would be added to every constant, with `""` or `List.of()` for the commands that have none. The refusals
shared by several commands are copied word for word into four or five constants. After this ticket a command is declared in
its own class, only with the data it has, and adding one is a class, an entry in `permits` and a line in `Capabilities`.

Why the enum and its exhaustive `switch` go (`31-10-2026-2259-gabier-has-a-help`, `31-10-2026-2348-gabier-is-composed-of-capabilities`):
they bought three things. A command without a capability does not compile: the sealed `Capability` keeps it. The order of
`gabier help` is explicit: the ordered list in `Capabilities` keeps it. The text is written once, next to the code: it moves
into the class of the command. `ServiceLoader` is rejected (the order of the help is lost, native image configuration, the list
only moves to `module-info`); the help read from `docs/commands/` is not this ticket (see `01-11-2026-0905-help-lists-every-refusal-a-command-can-answer.md`).

Shape aimed at (the worker confirms it against the code and chooses the names):
- `Command` becomes a `record` (name, summary, usage, example, refusals) whose invariants are `Assert`s of the compact
  constructor (non-blank parts, the usage names the command, the example starts with `$ gabier `, `List.copyOf`), built by a
  step builder (`.claude/rules/domain.md`): required parts are steps, so one left out does not compile, and the last step
  chooses the family of refusals (`asksTheDaemon(own...)`, `asksARunningDaemon()`, `standalone(refusals...)`). A future
  optional datum gets a default in the builder and a `withXxx(...)` on the commands that need it.
- Each `Capability` holds its `Command` (`Rename.COMMAND`) and `command()` returns it; the help text leaves `Command.java`.
- `Capability` is a `sealed interface`; `Capabilities` is the ordered list of the capabilities, in the order of `gabier help`,
  with `named`, `nearestTo` and `all()`; `Gabier`, `Help`, `HelpText` and `Standalone` read it.
- The shared refusals are constants of the `Refusal` holder, moved out of `Command` as a package-private type; their text does
  not change.
- The skill `add-daemon-method` (step 5) and the Javadoc of `Capability`, `Command` and `Capabilities` describe the new way
  to add a command, and the worker applies the step once on a throwaway command, then removes it (a mechanism meant to grow
  ships its skill).

Done when:
- [ ] The behaviour is identical: `GabierTest`, `DaemonClientTest` and `DaemonLauncherTest` pass **without any change**
      (`git diff` on them is empty), or with additions only if the coverage drops. Coverage stays at 100%.
- [ ] `Command` has no positional constructor with five arguments left, and a command declares only the data it has.
- [ ] `CommandRefusalsTest` and `HelpFlagTest` read `Capabilities.all()` with no assertion changed. `CapabilitiesTest` is
      removed (it checks wiring only) and so is `CommandTest`, which checks the implementation (non-blank fields, the form of
      a text), not a behaviour: the resolution says where each of its checks went (the `Assert`s of the descriptor, the names
      distinct by an `Assert` in `Capabilities`, the lookup by name already covered by `GabierTest` and `UnknownCommandTest`).
      No test of a behaviour is deleted or weakened.
- [ ] A capability left out of `permits` does not compile (shown by the worker with the throwaway command); no test compares
      `permits` with the list.
- [ ] The skill `add-daemon-method` (step 5) is updated and applied once, as above. `bin/ci` green.

Depends on: 01-11-2026-1041-gabier-inlines-a-variable-a-method-or-a-constant.md (same file: `inline` adds a constant to
`Command`; this ticket converts all the commands, `inline` included).
