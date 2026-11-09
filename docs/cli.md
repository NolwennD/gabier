# Command line

`gabier --version` prints `gabier <version>` (`0.1.0`: the version of the release, `0.0.0-dev` in a build made with no version given, see the README) on the standard output and exits with 0. Every refusal of
gabier (a usage error: any other argument, or none; no daemon running, for `status` and `stop`; a daemon that does not start...) is reported as a **problem object** on the
standard error, never on the standard output: one line of text by default, one line of JSON with `--json` (the option
can come anywhere on the command line).

```text
$ gabier --bogus
Usage error: Unsupported arguments; run gabier help for the commands, and gabier help <command> for the usage of one (usage-error; arguments=--bogus)
Not sure which command? Ask for the task: gabier help --for="<task>"
$ gabier --json --bogus
{"type":"urn:gabier:error:usage-error","title":"Usage error","detail":"Unsupported arguments; run gabier help for the commands, and gabier help <command> for the usage of one","key":"usage-error","parameters":{"arguments":"--bogus"},"help":"Not sure which command? Ask for the task: gabier help --for=\\"<task>\\""}
```

A first word one typo away from a command (`gabier organise-imports x.java`) is refused with the command it meant, in the
detail (`did you mean organize-imports?`) and in the `suggestion` parameter; see [help](commands/help.md).

A refusal can end with a **pointer to the help**: after the problem on the standard error, one more line, and with `--json` a
last member `help` of the problem object (`"help":"..."`; the other members and the exit code are the same). A first word that
names no command gets `Not sure which command? Ask for the task: gabier help --for="<task>"`. A command whose operation the
language server declined (`language-server-not-extractable`, `language-server-not-movable`, `editing-no-such-code-action`...)
gets `Usage and other ways: gabier help <command>`, with the command that was run.

`gabier help` lists the commands and `gabier help <command>` explains one, as does `gabier <command> --help` (or `-h`):
`gabier help agents` prints the lines to paste in the instructions of a coding agent; see [help](commands/help.md).
`gabier init-agent <skill|rule|snippet>` writes them into the project as a skill, a rule or a block of `AGENTS.md`, with no
daemon; see [init-agent](commands/init-agent.md). Its refusals (`init-agent-file-exists`, `init-agent-unknown-command`...) end
with the same pointer line, `Usage and other ways: gabier help init-agent`.

The JSON object follows the data model of [RFC 9457](https://www.rfc-editor.org/rfc/rfc9457) (problem details)
without its HTTP side: `type` (a URI that is not meant to be resolved, `urn:gabier:error:<key>`), `title`, `detail`,
and the extensions `key` and `parameters` (the machine-readable cause, as strings). It has no `status` member and
no `application/problem+json` media type. `title` and `detail` are English text. An error that gabier does not
know is reported as the generic `internal-server-error`, without its message, its cause or a stack trace.

**Starting ahead of the first command.** `gabier start` starts the daemon of the project and jdtls and answers at once;
`gabier start --wait [--timeout=<seconds>]` answers when jdtls is ready, writing where jdtls is on the standard error while
it waits (never on the standard output). Its refusals, `jdtls-start-failed` and `jdtls-start-timeout`, are problem
objects like the others, with exit code 1. See [start](commands/start.md).

## How the cli is built

The cli (`modules/cli`, package `eu.gabier.cli`) is a hexagon. The command line is its primary adapter, in
`eu.gabier.cli.infrastructure.primary` (`CommandLine`, the commands `Rename`, `Diagnostics`... and their tests); only `Gabier`,
the entry point and composition root, stays in `eu.gabier.cli`. The commands
read the command line (`Arguments`, `--json`) and print; they reach the daemon only through the port `Daemon` of
`eu.gabier.cli.domain` (a method with string parameters in, the members of its result out, a refusal thrown as a
`DaemonRefusal`), which an `Invocation` hands them with the arguments and the output. The adapter of the port,
`SocketDaemon`, is in `eu.gabier.cli.infrastructure.secondary` with what talks on the socket and starts the daemon
(`DaemonClient`, `DaemonLauncher`, `DaemonException`, `WaitNotice`); `Gabier` builds it and hands it to `CommandLine`, and no other class
outside that package names it (`HexagonalArchTest`). The text of a result (`ChangedFiles`, `FileDiagnostics`, `FoundReferences`,
`CodeActions`) is a pure function of the result, `ResultText` in `eu.gabier.cli.application`: the commands only print its
lines. A second front end, such as an MCP server, takes the same port and the same text.

## Text in a terminal

In text, the file and the message are shown with the characters a terminal obeys or does not show written as `\uXXXX`
escapes (`\u{XXXXX}` beyond the basic plane) and each backslash doubled, so that a message quoting source text cannot
drive the terminal, fake a line or hide behind a blank. Escaped: the control characters (C0, DEL, C1), the line and
paragraph separators (U+2028, U+2029), the format characters (bidirectional controls, zero-width characters, tag
characters), every space separator but the space (the no-break space U+00A0, U+1680, U+2000 to U+200A, U+202F, U+205F,
U+3000), the Hangul fillers (U+115F, U+1160, U+3164, U+FFA0), the braille blank U+2800, the combining grapheme joiner
U+034F, the variation selectors (U+FE00 to U+FE0F, U+E0100 to U+E01EF) and an unpaired surrogate. Kept as they are: the
space (U+0020), and every other character, such as an accent, an ideograph or an emoji. The same goes for every text
the cli prints from what the daemon or the user gave: the problem of a refusal, and the whole line of `gabier status`.
With `--json` the text is the exact one, and JSON escapes only what it must plus DEL and U+0080 to U+009F (written
`\u007f`, `\u009b`...), so that the C0, DEL and C1 controls of the JSON do not drive a terminal (the other characters above are written as they are in `--json`).

The commands are in [Capabilities](../README.md#capabilities); the daemon they talk to is in [The daemon](daemon.md) and the language they use with it in [Protocol](protocol.md).

## Exit codes

The exit code is derived from the problem. These codes are stable, for `gabier` and for `gabier-daemon`.

| Code | Meaning                                                                                                   |
| ---- | --------------------------------------------------------------------------------------------------------- |
| 0    | Success.                                                                                                  |
| 1    | Internal error: gabier failed (a bug, or the environment), the request was not at fault.                  |
| 2    | Request refused: a usage error, no daemon running, a socket path too long, any refusal of a request.      |
