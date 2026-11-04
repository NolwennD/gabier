# gabier help --for does not answer a task the guide does not cover with a wrong section

Opened: 04-11-2026 09:34

An agent that asks `gabier help --for="rename this method"` is told how to rename a method, or that the guide has no
answer, never about "A boolean variable that reads the wrong way". Met on 04-11-2026 by the worker of
`gabier-help-answers-a-question-about-a-task`: outside its table of questions, "rename this method" gave that section at
0.443, "run the build and the tests" gave "The package of one source set" at 0.43, "delete this unused import" gave "An
unused lambda parameter" at 0.303 (threshold 0.3). The guide covers improvements of code, not the commands gabier
already has (`rename`, `organize-imports`, `move`, `references`, `diagnostics`, `create`). Under the rule of
`dogfooding.md` a wrong answer is a friction.

Review of the ticket (04-11-2026) adds three cases, all shown on the branch of the ticket:
- a one-word question clears the threshold whatever the word: `--for=the` answers three sections at 0.91, `--for=block`
  three at 0.85, because the confidence of a one-word question does not depend on its idf;
- the variants of the question of the issue rank the wrong section first: "make this a text block" puts "A concatenation
  that is not only literals" first (0.337 against 0.317), "use a text block" too (0.608 against 0.478), and "convert
  string concatenation to text block" puts the right one second; the `Words:` line of the right section was edited to be
  the verbatim text of question 1, so the table is partly circular;
- a valid task abstains: "combine two ifs" gives 0.223 for "Conditions to join or to split".

Done when:
- [ ] red first: the three variants, the one-word questions and "combine two ifs" are in the table of
  `GuideQuestionsTest` with the expected rank (the table is not built from the `Words:` lines it is meant to test: the
  questions are written before the lines are changed, and the commit says which lines moved);
- [ ] red first: the three questions above and five more taken from the commands of `gabier help` are put in the table
  of `GuideQuestionsTest`; the guide has an entry for each command a person asks for by its task (rename a symbol, fix
  the imports, move a class, find where it is used, see the errors, create a file), or the answer says that the task is
  a command and names it;
- [ ] a question with a known word in common but no real answer abstains (a rule of abstention that does not rest on the
  confidence alone, or a threshold shown with the new table); the Javadoc of `THRESHOLD` cites only questions that are
  in `questionsWithNoAnswer`; `bin/ci` passes.
