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
