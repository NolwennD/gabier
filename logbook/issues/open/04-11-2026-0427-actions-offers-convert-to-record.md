# gabier actions offers Convert to record

Opened: 04-11-2026 04:27

An agent that has an immutable class (final fields, a constructor, accessors) can ask `gabier actions` whether it can
become a record. Met on 04-11-2026 by the worker of `a-guide-says-which-problem-each-improvement-solves`: jdtls 1.63.0
has `getConvertToRecordProposals` (`QuickAssistProcessor`), yet `gabier actions` never lists "Convert to record" on such
a class. The worker saw the action wrapped as a quick fix and wondered about the filter of `JdtlsLanguageServer` around
the kinds `quickfix`, `refactor` and `quickassist`; not investigated. `docs/improve.md` says "not offered" and gives the
by-hand route.

Done when:
- [ ] on the real jdtls (a throwaway replay that prints it), the cause is shown: jdtls does not offer the action for the
  class, or it offers it with a kind or a title that gabier drops;
- [ ] if gabier drops it, `actions` lists it and `apply-action` applies it, with a scenario replayed on the real jdtls;
  if jdtls does not offer it, `docs/improve.md` says why and the issue is closed with the reason; `bin/ci` passes.
