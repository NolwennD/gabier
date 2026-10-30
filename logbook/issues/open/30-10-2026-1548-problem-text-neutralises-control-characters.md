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
