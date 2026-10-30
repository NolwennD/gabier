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
