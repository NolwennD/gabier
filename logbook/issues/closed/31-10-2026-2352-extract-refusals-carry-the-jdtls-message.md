# The refusals of the extractions carry the message of jdtls

Opened: 31-10-2026 23:52
On hold: the refusal already names the cause in one sentence; lifted if an agent is seen unable to tell why a selection is not
extractable (31-10-2026 refocus).

Found in the review of `gabier-extracts-a-method`. When jdtls answers `RefactorEdit(edit=null, errorMessage=...)`, the refusal
(`language-server-not-extractable`, `language-server-not-an-expression`; `JdtlsLanguageServer.java:271`) drops that message, so an agent
cannot tell which rule failed (a `return` in a branch, a `break`...).

Done when:
- [ ] The problem of both refusals carries the `errorMessage` of jdtls (as a parameter, neutralised in text as every text that
      comes from the project), shown by a scenario replayed on the real jdtls; the docs say it.
- [ ] `bin/ci` green, coverage 100%.

## Resolution

Rejected, not done. The user decided on 01-11-2026 that gabier refuses in one way whatever jdtls answers, with a text of its own, and the ticket `01-11-2026-2144-extractions-refuse-in-one-way.md` (f35f2dd) did so. The worker found, on jdtls 1.63, that the premise of this issue was wrong: real jdtls never answers `errorMessage` for the four extractions, so there is no message to carry.
