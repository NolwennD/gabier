# Concatenated strings with escapes become text blocks

Opened: 30-10-2026 20:01

`.claude/rules/domain.md` ("Modern Java") asks for text blocks wherever a string spans several lines or escapes special
characters. The code written before has about thirty concatenations with `\"` or `\n` (JSON messages and fixtures of the
tests above all). One pass rewrites them, through jdtls and not by text editing (`.claude/rules/workflow.md`): its quick
assist "Convert to text block" is a code action, and the cleanup `stringConcatToTextBlock` is the same rewrite over a
file; the script `chatbot/.claude/tools/jdtls-driver.py` drives code actions of a kind (`refactor.extract.*`, `inline`)
and has no command for this one, so the worker adds what it needs next to its work, outside the repository, and does
not commit it. The first check is that jdtls really offers the action on the sites; if it does not, say so and stop.

Done when:
- every concatenation of literals with an escaped quote or a `\n` in the main and test code is a text block, or stays
  and says why in the resolution (a single line, an escape that is the point of the test); the list of the sites found
  by a search is in the resolution;
- text that is compared or written on one line (one JSON object per line on the socket) is unchanged to the byte: a
  line that was joined keeps its `\` continuation, a trailing space its `\s`; the tests still pass without being
  edited, and no assertion is weakened;
- no site is rewritten by hand when jdtls offers the action;
- `bin/ci` green, Checkstyle included.
