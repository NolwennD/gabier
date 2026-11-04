# gabier help --for does not answer a task the guide does not cover with a wrong section

Opened: 04-11-2026 09:34

An agent that asks `gabier help --for="rename this method"` is told how to rename a method, or that the guide has no
answer, never about "A boolean variable that reads the wrong way". Met on 04-11-2026 by the worker of
`gabier-help-answers-a-question-about-a-task`: outside its table of questions, "rename this method" gave that section at
0.443, "run the build and the tests" gave "The package of one source set" at 0.43, "delete this unused import" gave "An
unused lambda parameter" at 0.303 (threshold 0.3). The guide covers improvements of code, not the commands gabier
already has (`rename`, `organize-imports`, `move`, `references`, `diagnostics`, `create`). Under the rule of
`dogfooding.md` a wrong answer is a friction.

Done when:
- [ ] red first: the three questions above and five more taken from the commands of `gabier help` are put in the table
  of `GuideQuestionsTest`; the guide has an entry for each command a person asks for by its task (rename a symbol, fix
  the imports, move a class, find where it is used, see the errors, create a file), or the answer says that the task is
  a command and names it;
- [ ] a question with a known word in common but no real answer abstains (a rule of abstention that does not rest on the
  confidence alone, or a threshold shown with the new table); the Javadoc of `THRESHOLD` cites only questions that are
  in `questionsWithNoAnswer`; `bin/ci` passes.
