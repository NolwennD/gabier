# gabier help

`gabier help` lists the commands, one line each; `gabier help <command>` gives the usage of a command, one example and its
refusals, and, for a command that has some, notes (a warning to read before the call: `move-type-to-file`, `move`, `inline`, `create`, `references`, `actions` and `apply-action` have one). `gabier --help` and `gabier -h` are `gabier help`. `gabier <command> --help` and `gabier <command> -h` are
`gabier help <command>`, for every command, the same text (and the same JSON object with `--json`): the flag can stand
anywhere after the command, it wins over the other words (they are not read, so `gabier rename --token --help` is
not a usage error), and it starts no daemon. The answer is plain text on the standard output, exit code 0.
With `--json` the same content is one JSON object: `{"commands":[{"name":"...","summary":"...","usage":"..."}]}` for the
list, `{"name":"...","summary":"...","usage":"...","example":"...","refusals":["<key>: <meaning>"]}` for a command, with a
`"notes"` member (a string) before `"refusals"` for a command that has notes. A
command that does not exist (`gabier help frobnicate`) is a usage error, exit code 2, that lists the known ones; so is
anything else after `help <command>`, and `--help` followed by another argument.

`gabier help guide` prints [the guide of the improvements](../improve.md): one section per situation (a string built with
calls and quotes, a cast after an `instanceof`, a condition to turn round), with the words a person uses for it, the
command or the action that solves it, a before and an after, and the limits, among them what jdtls does not offer. It is
the page `docs/improve.md`, which the build copies into the resources of the cli and the native image keeps, so that it
is printed with no clone of the repository. `guide` is a topic of `help`, not a command (it has no row in the list of
commands and no `gabier guide`); `gabier help` points to it on its last lines. With `--json` it is one object,
`{"name":"guide","summary":"...","text":"..."}`, the text being the page. To add an entry, see the skill `extend-guide`.

`gabier help agents` prints the instructions to give a coding agent: a short section of plain Markdown (about fifteen
lines), written to be pasted as it is in `AGENTS.md` or `CLAUDE.md` (`gabier help agents >> AGENTS.md`). It says to start
the session with [`gabier start`](start.md), to ask `gabier help --for="<the task, in English>"` before editing Java by
hand, which commands replace a text edit, to try a change with `--dry-run` first, and to read the refusal of a command with
`gabier help <command>`. Like the guide it is a topic of `help`, not a command, and with `--json` it is one object,
`{"name":"agents","summary":"...","text":"..."}`. The text is the resource `agents.md` of the cli
(`modules/cli/src/main/resources/eu/gabier/cli/`, kept by the native image like the guide), the only copy; the class
`AgentInstructions` holds it, and [`gabier init-agent`](init-agent.md), which writes it into a project as a skill, a rule or a block of `AGENTS.md`, calls the same `text()`. A test
(`AgentInstructionsTest`) reads the commands the text cites in backticks (`` `gabier <command>` ``) and fails when one is
not a command in the list of `gabier help` (or a topic of `help`): change the text and the commands together.
`gabier help` lists it among its pointers (a line near its end), and so do the end message of the install and the first
steps of the README.

`gabier help --for="<task>"` asks the guide a question: the task in English ("turn this string concatenation into a text
block"), and the answer is one to three sections of the guide, best first, each with its title, its line `Ask:` or `By
hand:` and a confidence from 0 to 1 (two decimals):

```text
$ gabier help --for="turn this string concatenation into a text block"
For "turn this string concatenation into a text block":

1. A string built from literals with `+` and line breaks (confidence 0.53)
   Ask: `gabier cleanup --dry-run <file> stringConcatToTextBlock`

2. A concatenation that is not only literals (confidence 0.41)
   Ask: `gabier actions <file> ...
```

A task that no section answers is said plainly, with exit code 0 (it is an answer, not a refusal): `Nothing in the guide
matches "<task>".` With `--json` the answer is one object, `{"question":"...","matches":[{"rank":1,"title":"...",
"kind":"ask","how":"...","confidence":0.53}]}`, `kind` being `ask` or `by-hand` and `how` the line without its label; the
`matches` of a task that nothing answers is `[]`. `--for` takes its value after `=` (`--for="<task>"`): `--for <task>`
with a space is a usage error. In text the task is shown as [any text of a user is](../cli.md#text-in-a-terminal),
neutralised on one line, so that a task read from a page cannot forge an entry or drive the terminal; with `--json` it
is the exact text. An empty task (`--for=`, `--for`) is a usage error.

The sections are ranked by BM25 over the words of their title, their `Words:` line and their text (`ranking` package of
the cli, written in plain Java): a word is cut at case changes, punctuation, hyphens and digits, so
`stringConcatToTextBlock` is "string concat to text block"; there is no stemming, so the `Words:` line of an entry holds
the forms a person uses (`ifs` and `if` are two words). A section is part of the answer when it holds **at least two
distinctive words of the task in its title or its `Words:` line**, and its confidence (its score over the highest score
the words of the task could get) is at least 0.3 and at least 0.6 of the best one's; nothing else matches. A word is
distinctive when its idf is at least 1, which is a word that about a third of the sections hold at most **in their
title or their `Words:` line** (not anywhere in their text: "file" is in the text of 34 of the 43 sections and in the
title or the `Words:` line of 3, so `--for="new file"` answers): "the" and "run" are not, "text", "block" and "rename"
are. A demonstrative of the task ("this", "that", "these", "those") points at the code in front of the person and is not
a word of the task: `--for="rename this method"` is `--for="rename method"`, and `--for="this used"` has one word left,
so no answer. The consequence is that **a task of one word never has an answer**,
whatever the word (`--for=rename` says `Nothing in the guide matches "rename".`; `--for="rename a method"` answers), and
that a task whose distinctive words are spread over several sections, none holding two of them, such as "run the build
and the tests" (`tests` is in the `Words:` line of one section, `build` in the title of another), has none either. It
reads the guide of the build, so it needs no clone, no daemon and no network. A wrong answer, or no answer, to a real task is a friction: see the rule
`dogfooding.md`.

`help` never talks to the daemon (nor does [`init-agent`](init-agent.md)): it starts none, opens no socket and works with no project. It is
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
$ gabier help guide
# Improving Java with gabier: the problem each move solves
...
$ gabier help agents
## Java with gabier
...
$ gabier help frobnicate
Usage error: Unknown command; the commands are diagnostics, rename, organize-imports, cleanup, extract-variable, extract-method, extract-constant, extract-field, move-type-to-file, move, inline, create, references, actions, apply-action, start, status, stop, init-agent, help (usage-error; command=frobnicate)
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

**The pointer line.** A first word that names no command (a near one, a far one, none, and `gabier help frobnicate`) adds
a line to the usage error, after the problem on the standard error: `Not sure which command? Ask for the task: gabier help
--for="<task>"`; with `--json` it is the member `help` of the problem object, last, after `parameters`. A refusal where
the language server or the edit declined what was asked (the keys `language-server-not-extractable`, `-not-movable`,
`-not-inlinable`, `-not-creatable`, `-no-such-symbol`, `-not-applicable`..., `editing-no-such-code-action`,
`editing-unknown-clean-up`, and the refusals of [`init-agent`](init-agent.md) other than a failure of the file system: `HelpPointer`) adds `Usage and other ways: gabier help <command>`, with the name of the command
that was run, in the same two places. The key, the other members and the exit code do not change; a refusal that is a state
(`language-server-not-ready`, a daemon that does not start, a stale edit) and a usage error of a known command have no such
line, as they do not leave other ways to take. One class, `HelpPointer`, decides and builds the line.

## Refusals

A refusal is the problem object on the standard error, exit code 2. The key the command can answer, listed by `gabier
help help`:

- `usage-error`: the arguments are not those of the command (`gabier help <command>` gives its usage).
