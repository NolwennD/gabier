# A created file is reported once and shown with one diff

Opened: 01-11-2026 10:28

Found while using `gabier move-type-to-file` (friction, `.claude/rules/dogfooding.md`). The text answer of a command that creates a
file lists it twice (`modified Circle.java` and `created Circle.java`), and its unified diff has two header blocks (`--- /dev/null`
with no hunk, then `--- Circle.java` with the content). An agent reading the answer sees one file counted as modified and as
created. Once closed, a created file appears once, as created, with one diff.

Done when:
- [ ] `ChangeReport.modified()` does not list a file that the same change creates; text and `--json` agree (a test on the answer
      of `move-type-to-file`, with the fake).
- [ ] The unified diff of a created file is one block (`--- /dev/null`, `+++ <file>`, the content as one hunk); a test on the diff.
- [ ] The \"\ No newline at end of file\" line is kept only where the file really lacks the newline; the page
      `docs/commands/move-type-to-file.md` is updated to match.
- [ ] `bin/ci` green, coverage 100%.
