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
- [x] The behaviour is identical: `GabierTest`, `DaemonClientTest` and `DaemonLauncherTest` pass **without any change**
      (`git diff` on them is empty), or with additions only if the coverage drops. Coverage stays at 100%.
- [x] `Command` has no positional constructor with five arguments left, and a command declares only the data it has.
- [x] `CommandRefusalsTest` and `HelpFlagTest` read `Capabilities.all()` with no assertion changed. `CapabilitiesTest` is
      removed (it checks wiring only) and so is `CommandTest`, which checks the implementation (non-blank fields, the form of
      a text), not a behaviour: the resolution says where each of its checks went (the `Assert`s of the descriptor, the names
      distinct by an `Assert` in `Capabilities`, the lookup by name already covered by `GabierTest` and `UnknownCommandTest`).
      No test of a behaviour is deleted or weakened.
- [x] A capability left out of `permits` does not compile (shown by the worker with the throwaway command); no test compares
      `permits` with the list.
- [x] The skill `add-daemon-method` (step 5) is updated and applied once, as above. `bin/ci` green.

Depends on: 01-11-2026-1041-gabier-inlines-a-variable-a-method-or-a-constant.md (same file: `inline` adds a constant to
`Command`; this ticket converts all the commands, `inline` included).

## Resolution

Commit ac00057 (batch with `31-10-2026-2348-cli-tests-are-split-contracted-and-parameterised.md`). `Command` is a `record` (six components)
built by a step builder; its invariants are the `Assert`s of the compact constructor. `Capability` is a sealed interface, each capability
holds its `Command` (`COMMAND`), `Capabilities` is the ordered list (`all`, `named`, `nearestTo`, duplicate names refused), the shared
refusals are constants of `Refusal`. `CommandTest` and `CapabilitiesTest` are removed (their checks are the Asserts and the sealed
type). The skill `add-daemon-method` (step 5) describes the new way to add a command and was applied once on a throwaway command
(`permits` enforced by the compiler; `CommandRefusalsTest` fails only for the missing docs page), then removed.

Why: the enum and its exhaustive `switch` bought three things (a command without a capability does not compile, the order of `help`, the
text next to the code); the sealed type, the ordered list and the text in the class of the command keep them. `ServiceLoader` rejected
(order of the help lost, native image configuration).

Checked: the help output (`help`, `--json help` and the ten `help <command>` in both forms: 257 lines) is byte-identical before and after;
`GabierTest`, `DaemonClientTest`, `DaemonLauncherTest` are not in the diff. Worker's full `bin/ci` green on 36add0c (format 12s, build 5s,
test 154s with coverage 100%, native 163s, jdtls 437s). Review (neutral, once, both commits): no blocking finding. Suggestion left, opened
on hold: no test shows the `Assert`s of `Command` and `Capabilities` throwing.

This commit had been made by a first worker that was stopped when the user asked for `gabier create` first; its commit was kept
(`e639697`, cherry-picked) and checked point by point by the second worker. The new classes of this ticket are text edits, `gabier
create` not existing yet (`01-11-2026-1333-gabier-creates-a-class-an-interface-a-record-or-an-enum.md`).

Tokens: (batch of two tickets, the figures are the same in both resolutions)
- worker: 288023 (subagent_tokens, second worker, both tickets); the first worker, stopped before its notification, is not counted
- reviewer: 146098 (subagent_tokens, both commits)
- supervisor: 7 calls, 6184 output, 17974 input + cache write (1110811 cache read) over the supervisor session from the launch of the
  second worker (12:37 UTC) to the close, other work included; exchanges with the user: 0 messages
