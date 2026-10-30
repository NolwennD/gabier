# The path given to `gabier diagnostics` is the one the shell means, and the fake-served folder is not overwritten

Opened: 30-10-2026 20:36

Two findings of the review of `the-daemon-answers-diagnostics-of-a-file`:
- the cli sends `Path.of(file).toAbsolutePath().normalize()` (`Gabier.java:81`); `normalize()` is lexical, so with `src` a
  symlink to `/other/src`, `gabier diagnostics src/../Foo.java` asks for `cwd/Foo.java` while the shell resolves
  `/other/Foo.java`. The adapter normalises too (`JdtlsLanguageServer.java:128`);
- `FakeServedMain` (source set `fakeServed`) takes any folder and `writeProject` overwrites the files that exist there
  (a `Main.java` for instance). `bin/ci.d/40-native` passes a fresh temporary directory, so `bin/ci` is safe.

Done when:
- the symlink case is shown by a test (a temporary directory with a link) and the path sent is the one the shell
  means, or the choice not to resolve it is stated with the reason in the README; the cli does not normalise what the
  adapter normalises;
- `FakeServedMain` refuses a folder that is not empty, with a message, and a test shows it; a fresh folder works as
  before;
- `bin/ci` green, coverage 100%.

Depends on: 30-10-2026-2036-diagnostics-text-neutralises-control-characters (same file, `Gabier.java`).

## Resolution

Merged as `ce548f0` (fourth commit of the batch).

Checked as the first issue of the batch. Choice taken (the issue's "state it" option, confirmed by the review): the cli sends `Path.of(file).toAbsolutePath()` without `normalize()`; the daemon cleans the path lexically without following links. Resolving links in the cli (`toRealPath`) would make a file of a project reached through a symlinked folder look outside the project, because projects are matched as paths; the README says so, with an example. Test `doesNotCollapseTheDotDotOfAPathThroughALinkBecauseOnlyTheDaemonCleansPaths` (a temporary directory with `links/src -> targets/real`), red first. `FakeServedMain.serving(Path)` refuses a folder that is not empty or that is a file (`IllegalArgumentException`, exit 2); `FakeServedMainTest` has 4 tests, 2 fail with the check removed; the native stage runs the refusal on the real distribution. One existing test changed on purpose: `asksTheDaemonForTheAbsolutePathOfARelativeFileAndShowsTheFileAsTyped` now uses `src/Broken.java`, since its old input `src/../Broken.java` expected the cli to collapse the path.

Review (one pass): see the second issue. Left for the daemon follow-up issue: a dangling symlink passes `Files.notExists` in `FakeServedMain.requireNewOrEmpty`, and its new `catch (IllegalArgumentException)` is wider than the check.

Tokens:
- worker: shared with the batch, see the first issue (153359)
- reviewer: shared with the second issue (72243)
- supervisor: shared with the batch, see the first issue
