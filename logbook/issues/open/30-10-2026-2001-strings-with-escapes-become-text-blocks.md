# Concatenated strings with escapes become text blocks

Opened: 30-10-2026 20:01
Depends on: 01-11-2026-2230-gabier-cleans-up-a-file.md

`.claude/rules/domain.md` ("Modern Java") asks for text blocks wherever a string spans several lines or escapes special
characters. The code written before has about thirty concatenations with `\"` or `\n` (JSON messages and fixtures of the
tests above all). One pass rewrites them, through jdtls and not by text editing (`.claude/rules/workflow.md`): its quick
assist "Convert to text block" is a code action, and the cleanup `stringConcatToTextBlock` is the same rewrite over a
file. The pass is done with gabier itself (`.claude/rules/workflow.md`, dogfooding), by
`gabier cleanup <file> stringConcatToTextBlock`, not by a script. The first check is that jdtls really offers the action
on the sites; if it does not, say so and stop.

What the first check found (a worker, with a throwaway Python driver that is gone): jdtls lists the code action
`kind=quickassist title='Convert String concatenation to Text Block'` on `ProblemTest.java:102` when the range is inside
the string literal (column 36); on the method name (column 28) it is not offered. A dry run gave one edit, 102:32 to
104:82. The same request through `JdtlsSession` with `only=["quickassist"]` returned an empty list, probably because
`JdtlsCapabilities.forRefactoring()` does not declare the `quickassist` kind (not confirmed). Sites found by a search
(none rewritten): `ProblemTest:102-104`, `JsonRpcTest:31-32, 157, 268-275, 288-289`, `SelfCheckTest:97-98, 106-107`,
`Scenarios.java:168-170`, `GabierTest:43-45, 213-216`, `JsonRpc.java:42-44`, `DaemonStatus.java:27-28`,
`UnifiedDiffTest:214-216`; left on purpose: `ProblemTest:136` (the escapes are the point). Single literals with several
`\n` (`JdtlsLauncherTest:22, 39`, `TextFileTest:343`, `JdtlsLanguageServerTest:431`, others) were not tried.

Done when:
- every concatenation of literals with an escaped quote or a `\n` in the main and test code is a text block, or stays
  and says why in the resolution (a single line, an escape that is the point of the test); the list of the sites found
  by a search is in the resolution;
- the tests still pass without being edited, and no assertion is weakened (they prove that the text compared or written
  on one line is unchanged);
- no site is rewritten by hand when jdtls offers the action;
- `bin/ci` green, Checkstyle included.
