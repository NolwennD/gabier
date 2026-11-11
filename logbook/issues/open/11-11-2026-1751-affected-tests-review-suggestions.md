# The affected tests say what they miss and what they do not recognise

Opened: 11-11-2026 17:51
On hold: findings of the review of `gabier affected-tests` (PR 48) that break no `Done when` point and no use met yet;
lifted when a project shows one of them (a test run in parallel with a build, or `affected-tests /`, are the likeliest).

The review found cases of `TestSelector`, `TestRecognition`, `AffectedTests` and the page of the command that no test pins or
that the page does not say.

Done when:
- The tests that load their classes by themselves are described as the code recognises them (an `@ArchTest` or
  `@AnalyzeClasses` on a field or a method, as Infinitest does: see `ClassFacts` and `JavaAssistClass` of the clone of
  Infinitest the user gives), not as every ArchUnit test; a test with a `ClassFileImporter` and a plain `@Test`, such as
  `HexagonalArchTest` of this repository, is pinned as not dynamic; the page and the guide entry say it.
- A given Java file for which the build wrote no class (excluded by an Eclipse `excluding` pattern, an unannotated
  `package-info.java`) is covered by a test and by the page: the answer says what it is, and the tests that load their
  classes by themselves are not left out for that reason without saying so.
- `gabier affected-tests /` (a path with no file name) ends as a refusal, not as an `NullPointerException`; a test covers it.
- Two `affected-tests` calls at the same time cannot make one of them fail with `testing-read-failed` because the other
  rewrote class files while it read them: the class files are read while the language server is held, or the page says
  that the call can fail and why; a test or the page settles it.
- A test class with a custom runner and only private constructors: the choice (gabier recognises it, Infinitest does not) is
  stated among the differences from Infinitest on the page, and the test name that pins it says so.
- The statement about a constant read through a static import with the compiler of jdtls is proven by a test that compiles
  with the compiler of jdtls, or is worded as an observation.
- The resolution of the ticket 11-11-2026-1616 says that the Maven and Gradle layouts were observed by a throwaway probe,
  and that the replay in the branch exercises hand-written Eclipse projects only; a replay on a Maven and a Gradle project
  is added, or the page says which layouts are covered by a test.
