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
