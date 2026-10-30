# The text form of a problem neutralises format characters and is unambiguous

Opened: 30-10-2026 19:26

`Problem.toText` escapes the characters `Character.isISOControl` knows (and U+2028/U+2029), as
`problem-text-neutralises-control-characters` asked. Found in its review, still raw in a terminal:
- bidi controls and zero-width or tag characters (`Character.getType == FORMAT`): a detail `"evil‮gpj.txt"` shows a
  reversed extension, `"a​b"` hides a character;
- a backslash is not escaped, so an argument holding the six characters `\u001b` and one holding a real ESC print the
  same text; the JSON form has no such ambiguity;
- an unpaired surrogate (`"\ud800"`) passes through, untested and unstated.

Done when:
- every format character is shown visibly in text mode, a test per class (bidi, zero-width, tag), red first;
- the text form tells an escape from a literal backslash sequence (the backslash is escaped, or the limit is stated in
  the Javadoc with the reason), with a test;
- the behaviour on an unpaired surrogate is tested and stated;
- `gabier $'‮'` shown once on the native binary; `bin/ci` green.

Depends on: 30-10-2026-1548-problem-tests-prove-what-they-claim (same files: `Problem`, `ProblemTest`),
30-10-2026-2036-diagnostics-text-neutralises-control-characters (the neutralisation becomes one shared function).

## Resolution

Merged as `105369a` (third commit of the batch).

Checked as the first issue of the batch. `TerminalText.neutralise` works by code point: the format characters (`Character.FORMAT`) are shown as `\\uXXXX` (`\\u{e0041}` beyond the basic plane, the tag characters), a backslash is doubled so that an ESC prints `\\u001b` and the six typed characters print `\\\\u001b`, a lone surrogate is shown as `\\ud800` and a valid pair is kept. Tests in `ProblemTest.Text`, 8 red first: bidi U+202E, zero-width (U+200B, U+200D, U+FEFF), tags, escape against the same characters typed by hand, backslash, lone high and low surrogates (one at the end of the text), an emoji pair kept, a sweep over every code point. On the native binary `gabier 'evil<U+202E>gpj.txt'` printed the escaped name with exit 2 (the zero-width variant could not be run in the sandbox).

Review (one pass): see the second issue; the suggestions are in `terminal-text-hides-nothing`.

Tokens:
- worker: shared with the batch, see the first issue (153359)
- reviewer: shared with the second issue (72243)
- supervisor: shared with the batch, see the first issue
