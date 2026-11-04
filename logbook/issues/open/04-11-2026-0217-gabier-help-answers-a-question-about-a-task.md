# gabier help answers a question about a task

Opened: 04-11-2026 02:17
Depends on: 04-11-2026-0216-a-guide-says-which-problem-each-improvement-solves

An agent asks `gabier help --for="turn this string concatenation into a text block"` and gets the section of the guide
that answers, with the command to run, or says plainly that nothing answers, instead of reading the whole guide. It
works in the installed cli: no daemon, no clone, no network, no ripwire. The method is ripwire's (BM25 over the
sub-words of titles and text, a confidence, an abstention below a threshold), written in plain Java; ripwire stays a
tool of this repository that judges the ranking, not a dependency (decision of the user, 04-11-2026: do not reinvent, do
not require a tool the user lacks).

[stephanj/BM25](https://github.com/stephanj/BM25) (MIT, pure Java, one class of about a hundred lines) is the reference
for the formula, not a dependency: it is published nowhere (`org.example:BM25withJava:1.0-SNAPSHOT`, no release), its
stemmer is a third-party jar the core may not depend on without the user's decision (`quality.md`), and it splits on
whitespace only, so `stringConcatToTextBlock` and `text-block,` would not match "text block". gabier writes its own
ranker with the same formula (k1 = 1.5, b = 0.75), words split at case changes, punctuation and hyphens, a weight for
the title over the body, a confidence and an abstention. A stemmer is added only if the table of questions shows it is
needed, and then it is the user's decision.

[FeliciaCoding/bm25-search-engine-java](https://github.com/FeliciaCoding/bm25-search-engine-java) was looked at too: it
is a command-line application, not a library, over `maxmakovskiy/bm25`, which has no licence (nothing may be copied),
lives in GitHub Packages as a snapshot, needs OpenNLP and Jackson, drops one-letter words, does not split at case
changes and tests little. Both candidates write the same textbook formula (the Lucene idf, `k1` 1.2 or 1.5, `b` 0.75),
read, not run. gabier's tests therefore check the formula against a corpus of three short documents whose scores are
worked out by hand in the test, not copied from a run of any of these.

Done when:
- [ ] a table of 12 to 15 real questions (the logbook, the frictions met here, two of each kind) with the expected
  section and its rank, compared with the rank of `ripwire docs --recall="..."` on the same questions: gabier is not
  below it.
- [ ] the text block question ranks the clean-up first; "invert this if" answers with the known limit of jdtls; a
  question with no relation answers that nothing matches; `--json` has the same answer; the native cli of the release
  answers from a folder with no clone.
- [ ] `.claude/rules/dogfooding.md` says to ask before editing Java by hand; a wrong answer or an abstention on a real
  task is a friction, so a ticket. `bin/ci` passes.
