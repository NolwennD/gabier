# The scenarios keep the coupling to jdtls in the adapter

Opened: 02-11-2026 12:42

Decision of the user (02-11-2026): the coupling to jdtls is mastered over time, so that another Java language server stays possible.
The rule "Fake it until you run it" (`.claude/rules/workflow.md`) and the skill `add-scenario` change in two ways, and the fake keeps
the marginal cases. Today the replay compares the answer of jdtls with the scenario by `isEqualTo`: messages of the Eclipse compiler,
the exact shape of an edit, the indentation of a text block (97 mentions of jdtls in the fixtures); the 19 ids of `CleanUp` are in the domain.

1. **Observe first, then script.** A new answer of jdtls is observed on the real server before the scenario and the fake are written
   (a throwaway replay that prints it), not guessed and corrected at the end. The existing guard stays: the scenario states what the
   protocol and Java say, not a copy of a run; the observation is where it starts.
2. **Compare the effect, not the answer.** A scenario of the core states the effect: the text of the files once the change set is applied
   (rename, extract, move, inline, cleanup, organize-imports); for diagnostics the severity and the position, not the text of a message.
   The exact answer of jdtls (edits, messages, command arguments) is the business of the adapter: its tests keep the recorded exchange
   (in `modules/daemon`), the core never sees it. Another server that writes the same effect with other edits passes the same scenarios.
3. **The fake keeps the marginal cases**: faults that real jdtls cannot produce on demand (null answer, `InvalidRequest`, an edit with no
   message, a server that dies, a timeout, not ready). Each is labelled "fake only" with the reason why no replay can show it, and carries
   the handling by the adapter of a failure, never what jdtls answers when it is right.
4. **The coupling stays visible**: a number kept up to date and shown by the build (a test or a line of `bin/ci`): how many scenarios still
   compare an exact answer of jdtls instead of an effect, and how many values observed from jdtls (messages of the compiler, indentation)
   the core fixtures hold. It goes down; a ticket that raises it says why.

Existing scenarios migrate when a ticket touches them (not in one pass), the count of point 4 says how many remain. The ticket
`01-11-2026-1038-another-language-server-measures-the-jdtls-coupling.md` (on hold) stays the real measure.

Done when:
- [ ] `workflow.md` ("Fake it until you run it") and the skill `add-scenario` say the four points above, with the existing guard
      (not a copy of a run) kept; `AGENTS.md`/`verify` updated where they describe the replay.
- [ ] The replay compares the effect for the families of scenarios that have a change set (rename, extract, move, inline, cleanup,
      organize-imports) and the severity and position for diagnostics, through one comparison in the replay; the exact answer of jdtls is
      kept in the adapter tests; the scenarios already in the catalogue of one of these families are migrated (a family per commit if need
      be, say which are left, and the count of point 4 with them).
- [ ] The fake-only cases are labelled with their reason (a test fails on one without).
- [ ] The coupling number of point 4 is printed by `bin/ci` (or fails above a ceiling that only goes down). `bin/ci` passes.
