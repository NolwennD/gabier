# gabier help answers a question about a task

Opened: 04-11-2026 02:17
Depends on: 04-11-2026-0216-a-guide-says-which-problem-each-improvement-solves

An agent asks `gabier help --for="turn this string concatenation into a text block"` and gets the section of the guide
that answers, with the command to run, or says plainly that nothing answers, instead of reading the whole guide. It
works in the installed cli: no daemon, no clone, no network, no ripwire. The method is ripwire's (BM25 over the
sub-words of titles and text, a confidence, an abstention below a threshold), written in plain Java; ripwire stays a
tool of this repository that judges the ranking, not a dependency (decision of the user, 04-11-2026: do not reinvent, do
not require a tool the user lacks).

Done when:
- [ ] a table of 12 to 15 real questions (the logbook, the frictions met here, two of each kind) with the expected
  section and its rank, compared with the rank of `ripwire docs --recall="..."` on the same questions: gabier is not
  below it.
- [ ] the text block question ranks the clean-up first; "invert this if" answers with the known limit of jdtls; a
  question with no relation answers that nothing matches; `--json` has the same answer; the native cli of the release
  answers from a folder with no clone.
- [ ] `.claude/rules/dogfooding.md` says to ask before editing Java by hand; a wrong answer or an abstention on a real
  task is a friction, so a ticket. `bin/ci` passes.
