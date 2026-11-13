# The shared extraction refusal says no more than it knows

Opened: 01-11-2026 22:23
On hold: findings of the review of f35f2dd, none breaks a `Done when` point; lifted by the user or when a use makes one matter.

The refusal text and the docs of the four extractions claim a little more than the commands do.

Done when:
- [ ] `LanguageServerException.notExtractable` and `Refusal.NOT_EXTRACTABLE` do not name "statements" for `extract-variable`, `extract-constant` and `extract-field`, which never extract statements (a refused `return` keyword at 7:8-7:14 must not suggest a statement could work), without making the text differ by command for the same case.
- [ ] `gabier help extract-variable` ("One refusal for the four extractions") says which refusal scenarios are replayed for which command: the statement and void-call ones exist for `extract-variable` only.
- [ ] The lines over 120 columns of that paragraph and the broken Javadoc wrapping in `JavaLanguageServer.java` (about lines 54-56 and 68-70) and `JdtlsLanguageServer.java` (`extractConstant`) are re-wrapped.
