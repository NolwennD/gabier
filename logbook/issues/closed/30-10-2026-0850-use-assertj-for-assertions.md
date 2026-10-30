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

## Resolution

Done in e1390f4. AssertJ 3.27.7 (the latest stable release on Maven Central; 4.0.0-M1 is only a milestone) is in the
version catalog and is a `testImplementation` of `architecture`, `protocol`, `cli` and `daemon`; the `integrationTest`
source set inherits it. The test source sets have no `module-info` and run on the class path, so neither
`extraJavaModuleInfo` nor `gradle/modules.properties` needed an entry (`failOnAutomaticModules` did not fire for AssertJ
or byte-buddy). Six test files are migrated, and `.claude/rules/quality.md` says that assertions are AssertJ's.

Checked by the subagent: `bin/ci` green (format 1s, build 4s, test 19s, native 75s, jdtls 80s, with all six projects
listed by jdtls); `grep` finds no import of `org.junit.jupiter.api.Assertions`.

Review (one neutral reviewer, no blocking finding): two suggestions, both about idiom. `JdtlsProbeTest` keeps `fail(...)`
inside `if` blocks for the empty module list and the launcher check; `HexagonalArchTest` builds a description by
concatenation instead of `as("... %s", ...)`. They are small and are fixed by the supervisor right after the merge, the
probe being edited by the next two tickets.
