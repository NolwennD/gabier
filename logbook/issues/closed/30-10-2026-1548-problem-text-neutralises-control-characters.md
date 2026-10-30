# The text form of a problem neutralises control characters

Opened: 30-10-2026 15:48

`Problem.toText` neutralises line breaks only. Another control character from a parameter goes raw to the terminal:
`gabier $'\e[2J'` prints the ESC sequence on stderr in text mode (an agent or a person reading the output can be fed
terminal commands by a file name or an argument). The JSON form already escapes them.

Done when:
- [ ] In text mode, every control character (C0, DEL, C1) of a parameter or a detail is replaced visibly (for example by
      its escape `\u001b`), line breaks included; a test per class of characters, red first.
- [ ] `gabier $'\e[2J'` prints no ESC byte on stderr (shown once on the native binary).
- [ ] `bin/ci` green.

## Resolution

Merged as `9e2f0e0` (rebased three times on `main` as the other tickets landed).

Checked by the worker's `bin/ci` (full, on the rebased commit): format, build, test (100% coverage), native and jdtls
passed; the native stage was `UP-TO-DATE` on the last run, so the proof on the binary is the replay of the worker on the
binary built from its change: `gabier $'\e[2J'` exits 2, 0 ESC bytes on stderr (`tr -cd '\033' | wc -c`), stderr
`Usage error: Unsupported arguments; usage: gabier [--json] (--version | status | stop) (usage-error;
arguments=\u001b[2J)`; `--json` unchanged. `Problem.toText` runs the finished text through a `visible` step that replaces
every `Character.isISOControl` character (C0, DEL, C1) and U+2028/U+2029 with its `\uXXXX` escape, lowercase hex as in
`Json.quote`; the old `LINE_BREAKS` regex is gone. Tests (red first, 7 of 27 failed for the raw characters): one per
class in `ProblemTest.Text`. One existing test changed on purpose: `staysOnOneLine` became
`showsLineBreaksAsEscapesAndStaysOnOneLine` (the issue asks for line breaks to be replaced visibly; the one-line check
stays).

Review (one pass): no BLOCKING finding. Suggestions, grouped in the issue `text-form-of-a-problem-is-unambiguous`:
bidi, zero-width and tag characters pass through raw; a backslash is not escaped, so a literal `\u001b` and a real ESC
print the same; an unpaired surrogate is untested. Left: `containsNoControlCharacterWhateverTheInput` uses the same
predicate as the code and covers only U+0000 to U+00FF (redundant with the exact-string tests, harmless).

Tokens:
- worker: 80847 (its last completion notification; it was resumed by messages)
- reviewer: 41637
- supervisor: same measurement as `a-create-never-overwrites-a-file` (106618 output, 183726 input + cache write over 98
  calls, cache read 13428294; the four parallel tickets share it); exchanges with the user: 3 messages
