# gabier help

`gabier help` lists the commands, one line each; `gabier help <command>` gives the usage of a command, one example and its
refusals. `gabier --help` and `gabier -h` are `gabier help`. `gabier <command> --help` and `gabier <command> -h` are
`gabier help <command>`, for every command, the same text (and the same JSON object with `--json`): the flag can stand
anywhere after the command, it wins over the other words (they are not read, so `gabier rename --token --help` is
not a usage error), and it starts no daemon. The answer is plain text on the standard output, exit code 0.
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
$ gabier stop --help
Stop the daemon.
...
$ gabier help frobnicate
Usage error: Unknown command; the commands are diagnostics, rename, organize-imports, extract-variable, extract-method, status, stop, help (usage-error; command=frobnicate)
```

**Where the text lives.** The help text of every command is written once, in the enum `Command` of the cli
(`modules/cli`), next to the name that `Capabilities.of` switches on: a command added to the enum and given no capability
does not compile, and `CommandTest` fails on a command with no summary, usage, example or refusal. It is not
copied from these pages, which say more (the reasons, the limits); the help says what an agent needs to call the command
and read its refusals. When a command changes, change its constant and its page together. The refusals are the one part
the pages hold too: each page has a `## Refusals` section, one bullet per key (`` - `key`: what it means ``), and
`CommandRefusalsTest` reads the section and fails when the keys of the help of a command are not exactly those of its
page (`status` and `stop` share one page). The usage error of a call that
gabier does not understand does not repeat the usage: it points to `gabier help` and `gabier help <command>`.

## Refusals

A refusal is the problem object on the standard error, exit code 2. The key the command can answer, listed by `gabier
help help`:

- `usage-error`: the arguments are not those of the command (`gabier help <command>` gives its usage).
