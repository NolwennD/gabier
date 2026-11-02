# gabier

Coding agents that edit Java as text break packages, imports and references. gabier lets an agent change Java through
a Java language server instead: it drives jdtls, which works on the syntax tree, and answers with the files changed and
their diff, or a structured refusal. It is a command-line tool, `gabier`, backed by a daemon that keeps jdtls running
for the project; both are written in Java.

## Prerequisites

- A JDK 25 or later, to run the daemon (`JAVA_HOME`, else the `java` of the `PATH`).
- Python 3, to run the launcher of jdtls (a Python script; it also needs that JDK).
- GraalVM 25, to compile the native `gabier` binary.
- [mise](https://mise.jdx.dev), which installs the pinned tools and jdtls (see below).
- Optionally [SDKMAN!](https://sdkman.io), which selects the JDK pinned in `.sdkmanrc` when you enter the folder
  (`sdk env install` once); the build finds the GraalVM among its candidates.

## Install

`mise install` reads `mise.toml` and:

1. installs the pinned tools of the project: [hk](https://github.com/jdx/hk) (the Git hooks),
   [open-java-format](https://github.com/openjavaformat/open-java-format) (the formatter) and
   [ripwire](https://github.com/redhat-et/ripwire) (code search);
2. installs the Git pre-commit hook that formats the staged Java files (`hk install --mise`);
3. downloads the pinned jdtls (Eclipse JDT Language Server), checks its archive against the sha256 written in
   `mise.toml`, and unpacks it into `~/.local/share/jdtls/<version>`, outside the repository and shared by every checkout
   (from `https://download.eclipse.org/jdtls/milestones/<version>/`; `mise run jdtls:install` does this step alone);
4. exports, in a shell where mise is active, `GABIER_JDTLS` (the path of the jdtls launcher) and adds that launcher to
   the `PATH`.

Then `mise run install` builds the native `gabier` and the daemon `gabier-daemon` and links both into `~/.local/bin`
(`GABIER_BIN_DIR` to change it), so that `gabier` is called by name in any project. The native image takes a few
minutes. The details are in [The daemon](docs/daemon.md). The daemon starts by itself at the first command.

In a new Git worktree, run `mise trust` once first: mise trusts a configuration per path.

## Capabilities

- [`gabier diagnostics <file>`](docs/commands/diagnostics.md): the errors and warnings of a Java file, without a build.
- [`gabier rename`](docs/commands/rename.md): rename a symbol, and everything that refers to it, in every file.
- [`gabier organize-imports <file>`](docs/commands/organize-imports.md): add the missing imports, remove the unused
  ones and order the rest, never with a star import.
- [`gabier cleanup <file> <cleanup>...`](docs/commands/cleanup.md): apply clean-ups of jdtls to a file, such as
  `stringConcatToTextBlock`, which rewrites string concatenations into text blocks.
- [`gabier extract-variable`](docs/commands/extract-variable.md): turn an expression into a local variable.
- [`gabier extract-method`](docs/commands/extract-method.md): turn statements, or an expression, into a method.
- [`gabier extract-constant`](docs/commands/extract-constant.md): turn an expression into a constant.
- [`gabier extract-field`](docs/commands/extract-field.md): turn an expression into a field of the class.
- [`gabier move-type-to-file`](docs/commands/move-type-to-file.md): make a nested type a top-level type in a file of its
  own.
- [`gabier move`](docs/commands/move.md): move Java classes to a package, creating the package when it does not exist.
- [`gabier inline`](docs/commands/inline.md): replace a local variable, a method or a constant by its value (on the declaration of a method
  or constant: every use, and the declaration goes; on one use: that use only).
- [`gabier create`](docs/commands/create.md): create a file that holds an empty class, interface, record or enum, with the
  right `package` line and no comment.
- [`gabier references`](docs/commands/references.md): list where a symbol is used, in every file of the project and its
  tests (the declaration on request).
- [`gabier actions`](docs/commands/actions.md): list the fixes (a missing import) and the rewrites (a concatenation to a
  text block) the language server offers on a range of a file.
- [`gabier apply-action`](docs/commands/apply-action.md): apply one of them, chosen by its title.
- [`gabier help [<command>]`](docs/commands/help.md): list the commands, or explain one (usage, example, refusals).
- [`gabier status` and `gabier stop`](docs/commands/status-and-stop.md): see what the daemon serves, and stop it.

## Reference

[Command line](docs/cli.md) (`--json`, refusals, exit codes), [The daemon](docs/daemon.md),
[Protocol](docs/protocol.md). To work on gabier itself, see [AGENTS.md](AGENTS.md).

## License

gabier is released under the [Apache License 2.0](LICENSE).
