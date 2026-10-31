# The terminal text escapes every default-ignorable character

Opened: 30-10-2026 22:41
On hold: hardening with no effect on what an agent can do with gabier (exotic Unicode); taken up after the parity with
the Python driver (31-10-2026 refocus).

`TerminalText.neutralise` keeps characters that print as a blank or as nothing: U+17B4 and U+17B5 (Khmer inherent
vowels), U+180B to U+180D and U+180F (Mongolian variation selectors), U+FFF0 to U+FFF8 and unassigned code points that
are Default_Ignorable (`a⁥`), U+E0002 to U+E001F, U+E0080 to U+E00FF and U+E01F0 to U+E0FFF. `TerminalTextTest.Sweep`
expects them kept, so it pins the gap. `docs/cli.md` says that what hides behind a blank is escaped. Found in the review of
`terminal-text-hides-nothing`.

Done when:
- every code point whose Unicode property Default_Ignorable_Code_Point is set is escaped, and the hand-written list of
  the sweep says so (a test per range above, red first);
- the lone surrogates are covered by tests: a lone low surrogate, a reversed pair, a trailing high surrogate;
- the Javadoc and `docs/cli.md` name the rule (Default_Ignorable_Code_Point) and not a list of examples;
- coverage stays at 100%.
