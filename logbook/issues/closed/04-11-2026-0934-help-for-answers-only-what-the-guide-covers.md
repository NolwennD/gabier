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

## Resolution

Done in `41730c9` (pull request 17, a batch with `help-for-shows-the-question-safely`).

- The questions came first in `GuideQuestionsTest`: the three variants of the text block question, the one-word
  questions (`the`, `block`), "combine two ifs", and the commands asked by their task (rename, fix the imports, move a
  class, find where it is used, see the errors, create a file). `docs/improve.md` now has 43 sections: seven are new,
  one per command a person asks for by its task, each naming the command to run; the `Words:` lines that moved are
  listed in the commit.
- Rule of abstention in `Ranking.answer()`: an answer needs at least two distinctive words (idf of 1.0 or more) of the
  question in the title or the `Words:` line of a section, on top of the threshold. A one-word question never has an
  answer (`docs/commands/help.md` says so). The Javadoc of `THRESHOLD` cites only questions of
  `questionsWithNoAnswer`. `bin/ci.d/40-native` checks `help --for="rename this method"` on the native cli.
- Review (neutral, once, shared with the other issue): nothing blocking. Suggestions, opened as
  `help-for-answers-two-word-questions`: two-word questions that name a command abstain (`organize imports`, `new file`,
  `extract variable`, ...) because one word is only in the body of a section or is frequent; `this` counts as
  distinctive (`this used`, `rename this method` list extra entries); the abstention line should point to `gabier help`.
- Admitted by the worker: two cli tests were not watched red before the code, and one scratch use of Python outside
  the repository. Not repeated in the follow-up.
- `bin/ci` of the worker: format 15 s, build 37 s, test 232 s, native 176 s, jdtls 488 s, all passed; the check `bin-ci`
  of the pull request passed (12 min).

Tokens:
- worker: 263821 (`subagent_tokens`; one worker for the two issues of the batch, the figure is the same on both)
- reviewer: 87580 (`subagent_tokens`; one review for the batch)
- supervisor: see `help-for-shows-the-question-safely` (one figure for the batch)
