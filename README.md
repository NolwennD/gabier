# gabier

Coding agents that edit Java as text break packages, imports and references. gabier lets an agent change Java through
a Java language server instead: it drives jdtls, which works on the syntax tree, and answers with the files changed and
their diff, or a structured refusal. It is a command-line tool, `gabier`, backed by a daemon that keeps jdtls running
for the project; both are written in Java. An agent host that speaks [MCP](https://modelcontextprotocol.io) can use the
same operations as tools: `gabier mcp` is the server (see [step 4 of the first steps](#first-steps)).

An agent renames a method and every call to it in one command. This is the output of the installed `gabier` on a
two-file project (the paths are shortened to `/work/demo`):

```text
$ gabier rename --dry-run --token src/main/java/demo/Greeter.java greet welcome
dry run, nothing written
modified /work/demo/src/main/java/demo/Greeter.java
modified /work/demo/src/main/java/demo/Main.java

--- /work/demo/src/main/java/demo/Greeter.java
+++ /work/demo/src/main/java/demo/Greeter.java
@@ -2,7 +2,7 @@
 
 public class Greeter {
 
-    public String greet(String name) {
+    public String welcome(String name) {
         return "Hello, " + name;
     }
 }
--- /work/demo/src/main/java/demo/Main.java
+++ /work/demo/src/main/java/demo/Main.java
@@ -3,6 +3,6 @@
 public class Main {
 
     public static void main(String[] args) {
-        System.out.println(new Greeter().greet("world"));
+        System.out.println(new Greeter().welcome("world"));
     }
 }
```

Without `--dry-run`, the same command writes that diff to the two files, the declaration and the call, in one step. The
other refactorings work the same way (see [Capabilities](#capabilities)).

**What is released so far.** Linux on x86-64 only (no macOS, no Windows). It needs a JDK 25 or later, which runs the
daemon (`JAVA_HOME`, else the `java` of the `PATH`), and Python 3, which runs the launcher of jdtls. The project it works
on must be built with Gradle, Maven or Eclipse.

There are two ways to get it:

- **[Use gabier on your project](#use-gabier-on-your-project)**: use a release, pinned with mise or installed by a
  script. You need no clone and no build.
- **[Build gabier from its sources](#build-gabier-from-its-sources)**: for a version that is not released yet, or to
  work on gabier itself.

Both end with `gabier` on the `PATH`, and both are used the same way, as [Using it](#using-it) explains.

## Use gabier on your project

You need Linux on x86-64, a JDK 25 or later and Python 3, as said at the top of this page.

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
`mise install` changes the three together. Nothing else is set: no variable and no `postinstall`.
`~/.local/share/jdtls` and `~/.local/bin` are not used. mise prints nothing more: the [first steps](#first-steps) below
are the ones `install.sh` prints.

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

`GABIER_JDTLS` can name another launcher instead. When it is done, `install.sh` prints the folders it installed and the
[first steps](#first-steps) below.

### First steps

Whichever way you installed it:

1. `gabier --version` prints the version (if it is not found, `~/.local/bin` or the folder of mise is not in the `PATH`).
2. In a Java project, run `gabier start` (it starts the daemon and jdtls and answers at once; add `--wait` to wait until
   jdtls is ready), then a first command, such as `gabier diagnostics <a .java file>` (without `start`, the first answer
   waits for jdtls to import the project; see [Using it](#using-it)).
3. Give a coding agent its instructions: `gabier help agents` prints a short Markdown section (start `gabier start` at the
   beginning of a session, ask `gabier help --for="<the task>"` before editing Java by hand, the commands that replace a
   text edit, `--dry-run` first). Paste it as it is in `AGENTS.md` or `CLAUDE.md`, once (a second run adds a second copy), for instance:

   ```sh
   gabier help agents >> AGENTS.md
   ```

   Or let gabier write it where your project keeps the instructions of its agents, and keep it up to date with a new run
   after an upgrade: `gabier init-agent skill` (`.claude/skills/gabier/SKILL.md`), `gabier init-agent rule`
   (`.claude/rules/gabier.md`) or `gabier init-agent snippet` (a marked block of `AGENTS.md`, replaced in place by a second
   run). `--only=rename,diagnostics` keeps the commands you want taught, `--dry-run` shows the text first; see
   [`init-agent`](docs/commands/init-agent.md).
4. Or give an agent host that speaks [MCP](https://modelcontextprotocol.io) the operations as tools, with their
   instructions and no command line to learn. For Claude Code, from the project:

   ```sh
   claude mcp add gabier -- gabier mcp
   ```

   or, to share it with the team, a `.mcp.json` at the root of the project:

   ```json
   { "mcpServers": { "gabier": { "command": "gabier", "args": ["mcp"] } } }
   ```

   `/mcp` shows the server and its tools. The server starts the daemon in the background when the host connects; see
   [`mcp`](docs/commands/mcp.md).

   `claude mcp add` writes a server of your own (scope `local`, the default), which starts without a question. A
   `.mcp.json` is a server of the project: Claude Code asks each developer to approve it at the first interactive launch,
   and `claude mcp list` shows it as `Pending approval` until then. To approve it ahead of time, add
   `"enabledMcpjsonServers": ["gabier"]` to your own `~/.claude/settings.json` or to `.claude/settings.local.json`; the same
   line in the `.claude/settings.json` of the repository is ignored in a folder that Claude Code does not trust yet.

## Build gabier from its sources

You need:

- a JDK 25 or later and Python 3, as said at the top of this page;
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
  jdtls has imported the project, which takes seconds for a small project and a minute or more for a large one; after
  three seconds of waiting, `gabier` writes a line on the standard error that says so. The following commands are fast.
  `gabier start` does that wait ahead of time: it starts the daemon and jdtls and answers at once, so that an agent
  begins its session with it, works on something else, and finds jdtls ready at its first command (`gabier start --wait`
  returns when jdtls is ready).
- **One per project.** Each project has a daemon of its own. `gabier status` says what the daemon serves (and what jdtls is doing while it starts), and
  `gabier stop` stops it.
- **jdtls.** The daemon starts the jdtls that `GABIER_JDTLS` names, else the one shipped with it (the `jdtls/` folder of
  the archive that mise installs), else the pinned one under `~/.local/share/jdtls/<version>`, else a `jdtls` of the
  `PATH`. Without one, the first request says where it looked.
- **A project jdtls cannot import** (no Gradle, Maven or Eclipse build) is refused with `no-build-to-import`.

For a coding agent, point its instructions to `gabier help` (the line to give it is in the
[first steps](#first-steps)). It lists the commands, and `gabier help <command>` gives the usage, an example and the
refusals of each one. `gabier help --for="<the task>"` tells which command does a task. An agent host that speaks MCP
needs none of that: `gabier mcp` gives it the operations as tools, with the instructions and a `guide` tool that plays
the part of `gabier help --for` (see [`mcp`](docs/commands/mcp.md)); the tools take the same values as the commands, the
positions counted from 1 and `dryRun` in place of `--dry-run`.

## Capabilities

- [`gabier diagnostics <file>...`](docs/commands/diagnostics.md): the errors and warnings of Java files, without a build.
- [`gabier rename`](docs/commands/rename.md): rename a symbol, and everything that refers to it, in every file.
- [`gabier organize-imports <file>`](docs/commands/organize-imports.md): add the missing imports, remove the unused
  ones and order the rest as the project does (its `.settings` or `.vscode` preferences).
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
- [`gabier help guide`](docs/improve.md): which problem each improvement of the code solves (a text block, a switch
  expression, a condition to turn round...), what jdtls offers for it and what it leaves to you. The page is inside the
  installed cli: no clone needed.
- [`gabier help --for="<task>"`](docs/commands/help.md): ask the guide a question in English ("turn this string
  concatenation into a text block") and get the one to three sections that answer, with the command to run and a
  confidence, or the plain word that nothing matches. No daemon, no network.
- [`gabier help agents`](docs/commands/help.md): the lines to paste in the instructions of a coding agent (`AGENTS.md`,
  `CLAUDE.md`): `gabier start`, `gabier help --for=...` before editing by hand, the commands that replace a text edit.
- [`gabier init-agent <skill|rule|snippet>`](docs/commands/init-agent.md): write those instructions into the project as a
  skill, a rule or a marked block of `AGENTS.md`, with the commands you choose (`--only`) and the version of gabier that
  wrote them; a second run replaces the block. No daemon.
- [`gabier mcp`](docs/commands/mcp.md): the MCP server, for an agent host: one tool per operation, a `guide` tool that
  answers which tool does a task, the instructions of the server and the daemon started in the background.
- [`gabier start`](docs/commands/start.md): start the daemon and jdtls ahead of the first command; `--wait` returns when
  jdtls is ready.
- [`gabier status` and `gabier stop`](docs/commands/status-and-stop.md): see what the daemon serves, and stop it.

## Reference

[Command line](docs/cli.md) (`--json`, refusals, exit codes), [The daemon](docs/daemon.md),
[Protocol](docs/protocol.md). To work on gabier itself, see [AGENTS.md](AGENTS.md).

## License

gabier is released under the [Apache License 2.0](LICENSE).
