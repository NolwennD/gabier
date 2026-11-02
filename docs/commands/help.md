# gabier help

`gabier help` lists the commands, one line each; `gabier help <command>` gives the usage of a command, one example and its
refusals, and, for a command that has some, notes (a warning to read before the call: `move-type-to-file`, `move`, `inline`, `create` and `references` have one). `gabier --help` and `gabier -h` are `gabier help`. `gabier <command> --help` and `gabier <command> -h` are
`gabier help <command>`, for every command, the same text (and the same JSON object with `--json`): the flag can stand
anywhere after the command, it wins over the other words (they are not read, so `gabier rename --token --help` is
not a usage error), and it starts no daemon. The answer is plain text on the standard output, exit code 0.
With `--json` the same content is one JSON object: `{"commands":[{"name":"...","summary":"...","usage":"..."}]}` for the
list, `{"name":"...","summary":"...","usage":"...","example":"...","refusals":["<key>: <meaning>"]}` for a command, with a
`"notes"` member (a string) before `"refusals"` for a command that has notes. A
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
Usage error: Unknown command; the commands are diagnostics, rename, organize-imports, cleanup, extract-variable, extract-method, extract-constant, extract-field, move-type-to-file, move, inline, create, references, status, stop, remove-workspace, help (usage-error; command=frobnicate)
```

**Where the text lives.** The help text of every command is written once, in the class of its capability in the cli
(`modules/cli`), as the constant `COMMAND` built by `Command.called(...)`: a step builder asks for the summary, the usage,
the example and the family of refusals one after the other, so a command without one of them does not compile (the notes
are optional). The order of `gabier help` is the order of the list in `Capabilities`. It is not
copied from these pages, which say more (the reasons, the limits); the help says what an agent needs to call the command
and read its refusals. When a command changes, change its constant and its page together. The refusals are the one part
the pages hold too: each page has a `## Refusals` section, one bullet per key (`` - `key`: what it means ``), and
`CommandRefusalsTest` reads the section and fails when the keys of the help of a command are not exactly those of its
page (`status` and `stop` share one page). The usage error of a call that
gabier does not understand does not repeat the usage: it points to `gabier help` and `gabier help <command>`. When the
first word is one typo away from a command (a letter changed, added or removed, or two letters swapped), the usage error
also names it: `gabier organise-imports x.java` answers `did you mean organize-imports?` and carries a `suggestion`
parameter (`"suggestion":"organize-imports"` with `--json`). A word further than that gets no suggestion, and a typo
starts no daemon. The candidates are the names of `Command`.

## Refusals

A refusal is the problem object on the standard error, exit code 2. The key the command can answer, listed by `gabier
help help`:

- `usage-error`: the arguments are not those of the command (`gabier help <command>` gives its usage).
