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
