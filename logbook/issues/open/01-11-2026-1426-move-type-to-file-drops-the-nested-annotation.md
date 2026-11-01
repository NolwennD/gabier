# move-type-to-file drops @Nested when a class becomes top-level

Opened: 01-11-2026 14:26

Found while splitting the cli tests with `gabier move-type-to-file` (friction, `.claude/rules/dogfooding.md`):
`gabier move-type-to-file GabierTest.java 2014:11` made `WithoutADaemonTest` a top-level class that still carries `@Nested` (and its
import). JUnit does not run a top-level class annotated `@Nested`, silently, and a worker removed the annotation by hand in 12 files. Neither
the page of the command nor the help says what becomes of `@Nested`: they only warn that an *inner* `@Nested` class cannot be
instantiated by JUnit once it needs the outer instance. jdtls moves the text as it is and knows nothing of JUnit. Once closed, an
agent knows what happens to `@Nested` when it moves a test class, or the class comes out as a plain top-level class that runs.

**Care**: jdtls is configured, not fought; read first whether a setting or a request option of `java/move` gives it. If jdtls does not do
it, the decision between a documented limit in the help and the page and a gabier step after the move (an edit through the language
server, not a text script) is written in the resolution with the reason.

Done when:
- [ ] A scenario (replayed on the real jdtls) moves a nested class annotated `@Nested`: the answer is what jdtls gives, and the test says
      whether `@Nested` and its import remain.
- [ ] Either the moved class has no `@Nested` and no unused import of it, or the help (`gabier help move-type-to-file`) and
      `docs/commands/move-type-to-file.md` say that the annotation stays and how to remove it.
- [ ] `bin/ci` green, coverage 100%.
