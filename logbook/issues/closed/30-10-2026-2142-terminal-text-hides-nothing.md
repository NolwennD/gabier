# The terminal text hides nothing, and the docs say what it escapes

Opened: 30-10-2026 21:42

`TerminalText.neutralise` (used by `Problem.toText` and the text of `gabier diagnostics`) escapes the control, format
and line-separator characters. Found in the review of the batch that introduced it, still raw and able to hide text on a
terminal:
- other separators (`Character.SPACE_SEPARATOR`: U+00A0, U+3000, U+2000-200A, U+202F, U+205F), the Hangul fillers (U+3164,
  U+115F, U+1160, U+FFA0), the braille blank U+2800, U+034F and the variation selectors (U+FE00-FE0F, U+E0100-E01EF): a
  message `badㅤ` or `x⠀` shows blank;
- the README and the Javadoc of `TerminalText` (lines 11-24) say "control, format and separator characters" while the code
  escapes only U+2028 and U+2029 among the separators; those two are written as literal characters in the source
  (`TerminalText.java:44-45`, they read as two spaces);
- `gabier status` prints `status.version()`, the project and the jdtls state in text mode without `neutralise`: a daemon
  answering a `version` of `\u001b[2J` clears the terminal;
- `--json` escapes only the C0 characters: DEL and the C1 controls (U+009B is a CSI) reach the terminal raw, and
  `ProblemTest.writesNonAsciiAndOtherCharactersAsTheyAre` asserts DEL raw;
- the test `containsNoControlFormatOrSeparatorCharacterWhateverTheCodePoint` shares its predicates with the code and
  checks U+2028/2029 only: it cannot catch a class the code misses, and its name promises more.

Done when:
- each class above is shown as an escape in the text mode, a test per class, red first; a printable space (U+0020) and
  printable non-ASCII text (an accent, an ideograph, an emoji pair) stay as they are;
- the Javadoc and the README say exactly what is escaped and what is kept; U+2028/2029 are written with
  `Character.LINE_SEPARATOR` and `Character.PARAGRAPH_SEPARATOR` or `\u` escapes;
- the text of `gabier status` goes through the same function, a test with a hostile `version`;
- the JSON written by `Json.quote` escapes DEL and U+0080-U+009F as `\u00XX` (valid JSON), with a test, and the one that
  asserted DEL raw is changed with the reason said;
- the sweep test names what it checks and its expected side does not call the predicates of the code under test (a
  hand-written list of classes);
- `bin/ci` green, coverage 100%.

## Resolution

Merged as `7fad762` (first commit of the batch with `parameter-errors-follow-json-rpc`).

Checked by the worker's `bin/ci --all` on the final commit: format 5s, build 5s, test 101s (cli 68, core 259, daemon 339, protocol 315, shared 236, architecture 19, 0 failures), native 14s (binary recompiled with `--rerun`, status, stop, diagnostics, stale socket and fake-served project print ok), jdtls 239s (real jdtls, scenarios replayed). `TerminalText` switches on `Character.getType` (format, line and paragraph separators, spaces other than U+0020) plus the listed fillers, braille blank, U+034F, variation selectors, backslash and unpaired surrogates; the whole line of `gabier status` goes through it; `Json.quote` escapes DEL and U+0080 to U+009F. `TerminalTextTest.Sweep` goes over every code point against a table written by hand from the Unicode character database. Red first: 22 failures on the old code.

Changed tests, with the reason: the old `ProblemTest` sweep shared its predicates with the code and checked U+2028/2029 only, so it is replaced; two tests no longer hold DEL or U+00A0 raw, as the issue requires.

Review (one pass): nothing blocking. Suggestions: the invisible marks are an incomplete list (Default_Ignorable code points such as U+17B4, U+180B to U+180D, U+E0002 to U+E001F stay raw, and the sweep pins the gap), only one lone surrogate case is tested: issue `terminal-text-escapes-default-ignorable`. The README sentence that said the JSON "does not drive a terminal" overclaimed (a bidi override in `--json` stays raw): narrowed to the controls by the supervisor.

Tokens:
- worker: shared with the batch (190380, both issues)
- reviewer: shared with the batch (79226, both issues)
- supervisor: covers the session over the period of the batch (20:53 to 21:40 UTC), other work included: 78 calls, 82462 output, 219339 input + cache write (28818542 cache read); exchanges with the user: 10 messages
