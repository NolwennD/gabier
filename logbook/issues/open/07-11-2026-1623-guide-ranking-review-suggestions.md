# help --for: the demonstratives are proven, and the margin of the threshold is visible (review suggestions)

Opened: 07-11-2026 16:23
On hold: findings of the review of `help-for-answers-two-word-questions`; none breaks a `Done when` point or a real use.

- `modules/cli/src/test/java/eu/gabier/cli/ranking/RankingTest.java` (`aDemonstrativeOfTheQuestionIsNotAWordOfTheTask`):
  the checks for "those", "that" and "these" pass whatever `DEMONSTRATIVES` holds, since no document holds those words
  (`rank("those alpha")` equals `rank("alpha")`); reducing the set to `Set.of("this")` keeps every test green.
- `modules/cli/src/main/java/eu/gabier/cli/ranking/Ranking.java:48`: the margin of `THRESHOLD` shrank, "remove imports I
  don't use" scores 0.338 against 0.3 (0.352 before); one more `Words:` line can push it under with no failing test that
  names the cause.
- `gabier help help`: a line well over 120 columns.

Done when:
- [ ] a document holds "that", "these" and "those" in its keywords and the test asserts `answer("that alpha")` is empty,
      red with the set reduced to `this`;
- [ ] the margin of the threshold is stated in a test (the lowest score of a question the guide must answer, with its
      distance to the threshold), so a drop is named;
- [ ] the lines of `help.md` are rewrapped to 120 columns; `bin/ci` passes.
