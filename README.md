# gabier

Coding agents that edit Java as text break packages, imports and references. gabier lets an agent change Java through
a Java language server instead: it drives jdtls, which works on the syntax tree, and answers with the files changed and
their diff, or a structured refusal. It is a command-line tool, `gabier`, backed by a daemon that keeps jdtls running
for the project; both are written in Java.

## Install

`mise install`, then `mise run install`: it builds the native `gabier` and the daemon `gabier-daemon` and links both into
`~/.local/bin`, so that `gabier` is called by name in any project. The build needs GraalVM 25 and a JDK; the details are
in [The daemon](docs/daemon.md). The daemon starts by itself at the first command.

## Capabilities

- [`gabier diagnostics <file>`](docs/commands/diagnostics.md): the errors and warnings of a Java file, without a build.
- [`gabier rename`](docs/commands/rename.md): rename a symbol, and everything that refers to it, in every file.
- [`gabier organize-imports <file>`](docs/commands/organize-imports.md): add the missing imports, remove the unused
  ones and order the rest, never with a star import.
- [`gabier extract-variable`](docs/commands/extract-variable.md): turn an expression into a local variable.
- [`gabier extract-method`](docs/commands/extract-method.md): turn statements, or an expression, into a method.
- [`gabier move-type-to-file`](docs/commands/move-type-to-file.md): make a nested type a top-level type in a file of its
  own.
- [`gabier inline`](docs/commands/inline.md): replace a local variable, a method or a constant by its value, and remove
  its declaration.
- [`gabier help [<command>]`](docs/commands/help.md): list the commands, or explain one (usage, example, refusals).
- [`gabier status` and `gabier stop`](docs/commands/status-and-stop.md): see what the daemon serves, and stop it.

## Reference

[Command line](docs/cli.md) (`--json`, refusals, exit codes), [The daemon](docs/daemon.md),
[Protocol](docs/protocol.md). To work on gabier itself, see [AGENTS.md](AGENTS.md).

## License

gabier is released under the [Apache License 2.0](LICENSE).
