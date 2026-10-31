# Command line

`gabier --version` prints `gabier <protocol version>` on the standard output and exits with 0. Every refusal of
gabier (a usage error: any other argument, or none; no daemon running, for `status` and `stop`; a daemon that does not start...) is reported as a **problem object** on the
standard error, never on the standard output: one line of text by default, one line of JSON with `--json` (the option
can come anywhere on the command line).

```text
$ gabier --bogus
Usage error: Unsupported arguments; run gabier help for the commands, and gabier help <command> for the usage of one (usage-error; arguments=--bogus)
$ gabier --json --bogus
{"type":"urn:gabier:error:usage-error","title":"Usage error","detail":"Unsupported arguments; run gabier help for the commands, and gabier help <command> for the usage of one","key":"usage-error","parameters":{"arguments":"--bogus"}}
```

`gabier help` lists the commands and `gabier help <command>` explains one: see [help](commands/help.md).

The JSON object follows the data model of [RFC 9457](https://www.rfc-editor.org/rfc/rfc9457) (problem details)
without its HTTP side: `type` (a URI that is not meant to be resolved, `urn:gabier:error:<key>`), `title`, `detail`,
and the extensions `key` and `parameters` (the machine-readable cause, as strings). It has no `status` member and
no `application/problem+json` media type. `title` and `detail` are English text. An error that gabier does not
know is reported as the generic `internal-server-error`, without its message, its cause or a stack trace.

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
