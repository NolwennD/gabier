# Tests assert with AssertJ

Opened: 30-10-2026 08:50

The project's tests assert with AssertJ, not with `org.junit.jupiter.api.Assertions`. Add AssertJ (version in the
catalog, declared the way the other third-party jars are) to the test dependencies of every module that has tests, and
migrate every existing assertion to it, in the unit tests, the architecture tests and the `integrationTest` source set.

Done when:
- no test imports `org.junit.jupiter.api.Assertions` (check with `grep -r`), and the assertions read as AssertJ idiom
  (`assertThat(...)`, `assertThatThrownBy`, ...), not as a transliteration;
- AssertJ's Maven coordinates and version follow the same convention as the other dependencies, and `bin/ci` is green
  (coverage, architecture, native image and the module path included);
- `.claude/rules/quality.md` says that assertions are AssertJ's.
