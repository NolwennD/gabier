# gabier help

`gabier help` lists the commands, one line each; `gabier help <command>` gives the usage of a command, one example and its
refusals. `gabier --help` and `gabier -h` are `gabier help`. The answer is plain text on the standard output, exit code 0.
With `--json` the same content is one JSON object: `{"commands":[{"name":"...","summary":"...","usage":"..."}]}` for the
list, `{"name":"...","summary":"...","usage":"...","example":"...","refusals":["<key>: <meaning>"]}` for a command. A
command that does not exist (`gabier help frobnicate`) is a usage error, exit code 2, that lists the known ones; so is
anything else after `help <command>`, and `--help` followed by another argument.

`help` is the one command that never talks to the daemon: it starts none, opens no socket and works with no project. It is
not a daemon method, so it has no row in [the protocol](../protocol.md#methods).

```text
$ gabier help
gabier changes Java through a Java language server.
...
Commands:
  diagnostics       the errors and warnings of a Java file, without a build
  rename            rename a symbol, and everything that refers to it, in every file
...
$ gabier help stop
Stop the daemon.

Usage:
  gabier [--json] stop
...
$ gabier help frobnicate
Usage error: Unknown command; the commands are diagnostics, rename, organize-imports, extract-variable, extract-method, status, stop, help (usage-error; command=frobnicate)
```

**Where the text lives.** The help text of every command is written once, in the enum `Command` of the cli
(`modules/cli`), next to the name that `Capabilities.of` switches on: a command added to the enum and given no capability
does not compile, and `CommandTest` fails on a command with no summary, usage, example or refusal. It is not
copied from these pages, which say more (the reasons, the limits); the help says what an agent needs to call the command
and read its refusals. When a command changes, change its constant and its page together. The usage error of a call that
gabier does not understand does not repeat the usage: it points to `gabier help` and `gabier help <command>`.
