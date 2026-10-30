# The text of `gabier diagnostics` neutralises control characters

Opened: 30-10-2026 20:36

`gabier diagnostics <file>` prints each problem as `file:line:column: severity: message` (`Gabier.java:91-92`) with the
message as jdtls gave it and the file as typed. A message can quote source text: one holding `\u001b[2J` clears the
terminal, one holding a line break splits the one-line-per-problem format, and its second line can be forged as
`x.java:1:1: error: ...`. A file name with an ESC (`gabier diagnostics $'a\u001b[2Jb.java'`) is echoed raw too, a lesser
risk, since it is the caller's own input. `Problem.toText` already shows these characters as escapes
(`problem-text-neutralises-control-characters`); `--json` is safe. Found in the review of
`the-daemon-answers-diagnostics-of-a-file`.

Done when:
- the message and the file name printed in text mode go through the same neutralisation as `Problem.toText` (one
  function, shared, not copied), line breaks included: a test with a message holding `\u001b[2J` and one holding `\n`
  and a forged second line, red first; the output stays one line per problem;
- `--json` is unchanged; the clean-file case still prints nothing;
- `bin/ci` green, coverage 100%.

Coupled with `text-form-of-a-problem-is-unambiguous` (same function): this one is done first, that one follows.

## Resolution

Merged as `7f9ced9` (second commit of the batch).

Checked as the first issue of the batch. New class `protocol.TerminalText.neutralise(CharSequence)`, the one function that `Problem.toText` and the text of `gabier diagnostics` (file, position, severity, message) both use. Tests in `GabierTest.Diagnostics`, red first because the output was raw: a message with `ESC[2J` prints `\\u001b[2J`; a message with `\n` and a forged second line stays one line; a file name with `ESC[2J` is escaped; `--json` is unchanged; a clean file still prints nothing.

Review (one pass, of the commits 2 to 4 of the batch): no BLOCKING finding. Suggestions, in the issue `terminal-text-hides-nothing`.

Tokens:
- worker: shared with the batch, see the first issue (153359)
- reviewer: 72243 (the commits 2 to 4)
- supervisor: shared with the batch, see the first issue
