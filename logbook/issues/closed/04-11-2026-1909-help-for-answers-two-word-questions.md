# gabier help --for answers a short question that names a command

Opened: 04-11-2026 19:09

Seen in the review of `help-for-answers-only-what-the-guide-covers`. The abstention rule asks for two distinctive words
of the question in the title or the `Words:` line of a section, so a short and legitimate question gets `Nothing in the
guide matches`: `organize imports` (`organize` is only in the body), `show errors`, `list references`, `new file`
(`file` is in 34 of 43 sections), `text blocks`, `extract variable`, `inline variable`. An agent that types the name of
the task as it knows it must get the command. In the other direction, `this` counts as distinctive (idf 1.34), so `this
used` answers "Fields used without `this.`" first, and `rename this method` lists two sections after the right one.

Done when:
- [ ] each of the seven questions above answers the section of its command, as first answer, and `the` and `block`
      still abstain (tests in `GuideQuestionsTest`; the fix is in the `Words:` lines and in the rule, not a list of
      questions);
- [ ] `this used` does not answer "Fields used without `this.`" first, and `rename this method` answers the rename
      section alone (a test that asserts the other entries are absent, not only the first rank);
- [ ] the line `Nothing in the guide matches` points to `gabier help` (the list of commands);
- [ ] `bin/ci` passes.

## Resolution

Done in 27a2eb0.

- Cause: a word was "distinctive" by its idf over the whole text of the sections, so a word found only in a body
  (`file`, 34 of 43 sections) was no evidence. The idf is now counted over the title and the `Words:` line only
  (`file` in 3 sections, `the` in 21, `this` in 4), and `this`, `that`, `these`, `those` (`DEMONSTRATIVES`, in
  `Ranking`) are left out of the question: a rule, not a list of questions. Four `Words:` lines of `docs/improve.md`
  gained the name of their task ("organize or fix imports", "see or show the errors", "list references", "text blocks").
  The threshold comment went from 0.352 to 0.338 for "remove imports I don't use".
- Proof: `GuideQuestionsTest`: seven new rows (`organize imports`, `show errors`, `list references`, `new file`,
  `text blocks`, `extract variable`, `inline variable`) red first, then green; `the` and `block` still abstain;
  `this used` no longer answers "Fields used without `this.`"; `rename this method` answers "A name to change
  everywhere" alone. `RankingTest`: three new cases. The line `Nothing in the guide matches` already pointed to
  `gabier help` (`AskingTheGuideTest`), unchanged.
- The batch was reviewed once (no blocking finding); the suggestions are in `guide-ranking-review-suggestions`, `offered-action-title-leaves-the-core` and `main-test-wait-names-its-cause`, all on hold. `bin-ci` of pull request 20: green. The worker rebased over the code of the GitHub-page ticket and ran `bin/ci --all` again: format, build, test, release-install, native, jdtls all passed.

Tokens (one batch of three issues, shared by the three resolutions):
- worker: 221860
- reviewer: 87714
- supervisor: 53 calls, 42849 output, 102368 input + cache write (6262353 cache reads), over the period from the launch of
  the worker to the merge, the ticket of the GitHub page and the other work included; exchanges with the user: 3 messages
