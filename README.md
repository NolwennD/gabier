# gabier

Coding agents that edit Java as text break packages, imports and references. gabier lets an agent change Java through
a Java language server instead: it drives jdtls, which works on the syntax tree, and answers with the files changed and
their diff, or a structured refusal. It is a command-line tool, `gabier`, backed by a daemon that keeps jdtls running
for the project; both are written in Java.

There are two ways to get it:

- **[Use gabier on your project](#use-gabier-on-your-project)**: use a release, pinned with mise or installed by a script. You need
  no clone and no build.
- **[Build gabier from its sources](#build-gabier-from-its-sources)**: for a version that is not released yet, or to
  work on gabier itself.

Both end with `gabier` on the `PATH`, and both are used the same way, as [Using it](#using-it) explains.

## Use gabier on your project

You need:

- Linux on x86-64 (the only platform released so far);
- a JDK 25 or later, which runs the daemon (`JAVA_HOME`, else the `java` of the `PATH`);
- Python 3, which runs the launcher of jdtls.

### With mise

If your project already pins its tools with [mise](https://mise.jdx.dev), pin gabier the same way:

```sh
mise use github:NolwennD/gabier@<version>
```

The `mise.toml` of the project then holds one line, next to the JDK 25 (`java = "temurin-25"`) that runs the daemon:

```toml
[tools]
java = "temurin-25"
"github:NolwennD/gabier" = "<version>"
```

`mise install` in each clone gives the same `gabier`: the cli, the daemon it starts and the jdtls the daemon starts all
come from one archive of the release, so they are of one version, and changing the version in `mise.toml` and running
`mise install` changes the three together. Nothing else is set: no variable and no `postinstall`. Python 3 must be
installed, for the launcher of jdtls. `~/.local/share/jdtls` and `~/.local/bin` are not used.

### With the files of the release

Each [release](https://github.com/NolwennD/gabier/releases) holds five files:

- `gabier-linux-x86_64`, the native cli;
- `gabier-daemon-<version>.tar.gz`, the daemon: its start script and its jars;
- `gabier-linux-x86_64.tar.gz`, what mise installs: the cli, the daemon and jdtls in one archive;
- `install.sh`, which installs the cli and the daemon, and jdtls;
- `SHA256SUMS`, the checksums of the four files above.

Download the cli, the daemon, `install.sh` and `SHA256SUMS` into one folder, then:

```sh
sh install.sh
gabier --version
```

`install.sh` checks the files against `SHA256SUMS` and installs nothing if one does not match. It **copies** the cli and
the daemon into `~/.local/share/gabier/<version>`, links `gabier` and `gabier-daemon` from `~/.local/bin` to that copy,
and installs the jdtls milestone that gabier is tested against (named in the release notes) into
`~/.local/share/jdtls/<version>`, where the daemon looks for it. It downloads that jdtls from Eclipse and checks its
archive against the sha256 the script carries; a jdtls already installed there is left alone. `~/.local/bin` must be in
the `PATH`.

`GABIER_BIN_DIR` and `GABIER_INSTALL_DIR` change the folder of the links and the one of the copies, as for
[`mise run install`](#build-gabier-from-its-sources). `JDTLS_HOME` changes the folder of jdtls; then name its launcher
for the daemon with `GABIER_JDTLS=<JDTLS_HOME>/bin/jdtls`, since the daemon looks only in `~/.local/share/jdtls`.

`GABIER_JDTLS` can name another launcher instead.

## Build gabier from its sources

You need:

- a JDK 25 or later and Python 3, as above;
- GraalVM 25, which compiles the native cli;
- [mise](https://mise.jdx.dev), which installs the pinned tools and jdtls;
- optionally [SDKMAN!](https://sdkman.io), which selects the JDK pinned in `.sdkmanrc` when you enter the folder
  (`sdk env install` once). The build finds the GraalVM among its candidates.

```sh
git clone https://github.com/NolwennD/gabier && cd gabier
mise trust && mise install
mise run install
```

`mise install` reads `mise.toml` and:

1. installs the pinned tools of the project: [hk](https://github.com/jdx/hk) (the Git hooks),
   [open-java-format](https://github.com/openjavaformat/open-java-format) (the formatter) and
   [ripwire](https://github.com/redhat-et/ripwire) (code search);
2. installs the Git pre-commit hook that formats the staged Java files (`hk install --mise`);
3. downloads the pinned jdtls from `https://download.eclipse.org/jdtls/milestones/<version>/`, checks its archive
   against the sha256 written in `mise.toml`, and unpacks it into `~/.local/share/jdtls/<version>` (`mise run
   jdtls:install` does this step alone);
4. in a shell where mise is active, exports `GABIER_JDTLS` (the path of the jdtls launcher), which the tests of gabier
   read.

`mise run install` builds the native `gabier` and the daemon, which takes a few minutes. It **copies** them into
`~/.local/share/gabier/<version>` and links `gabier` and `gabier-daemon` from `~/.local/bin` to that copy.
`GABIER_INSTALL_DIR` and `GABIER_BIN_DIR` change the two folders. The copy does not depend on the clone: a `./gradlew
clean`, or a checkout of another version, leaves it working. Run `mise run install` again to install a new state of the
clone. To change gabier itself, read [AGENTS.md](AGENTS.md).

## Using it

Run `gabier` from anywhere inside a Java project built with Gradle, Maven or Eclipse: no file, variable or
configuration is added to that project.

```sh
cd ~/my-project
gabier diagnostics src/main/java/com/example/App.java
gabier rename --dry-run --token src/main/java/com/example/App.java App Application
```

- **The project.** It is the nearest folder above the working directory that holds a `.git`, else the working
  directory. `GABIER_PROJECT` names another one.
- **The daemon.** The first command starts a daemon for that project, which starts jdtls. The first answer waits until
  jdtls has imported the project, which takes seconds for a small project and a minute or more for a large one. The
  following commands are fast.
- **One per project.** Each project has a daemon of its own. `gabier status` says what the daemon serves, and
  `gabier stop` stops it.
- **jdtls.** The daemon starts the jdtls that `GABIER_JDTLS` names, else the one shipped with it (the `jdtls/` folder of
  the archive that mise installs), else the pinned one under `~/.local/share/jdtls/<version>`, else a `jdtls` of the
  `PATH`. Without one, the first request says where it looked.
- **A project jdtls cannot import** (no Gradle, Maven or Eclipse build) is refused with `no-build-to-import`.

For a coding agent, point its instructions to `gabier help`. It lists the commands, and `gabier help <command>` gives
the usage, an example and the refusals of each one.

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
