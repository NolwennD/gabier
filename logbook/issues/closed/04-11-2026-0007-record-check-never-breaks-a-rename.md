# The record check never breaks a rename that would have worked

Opened: 04-11-2026 00:07

Finding of the review of `rename-a-record-component`. Since `d414620` every rename of a symbol declared in a project file
asks jdtls for `definition` and for `semanticTokens/full` of the declaration file before the rename. If one of those
two answers with an error (for instance -32603), every rename fails with `internal-server-error`, a class or a method
included, which jdtls would have renamed correctly. An agent loses renames that worked before the check.

Done when:
- a failure of `definition` or of `semanticTokens/full` (an error answer, a timeout) does not stop the rename: the check
  is skipped and the rename goes on as before `d414620`; a test per request with a failed future, in
  `JdtlsLanguageServerTest`;
- the cost of the semantic tokens of a whole file is measured on the biggest file of the project and said in the
  `## Resolution` (the request is only needed when the declaration is a record component, see whether `definition` or
`documentSymbol` can rule out the other symbols first);
- a record with an explicit accessor (`public String file() { ... }`) is observed on the real jdtls: `definition` from a
  call of the accessor points to the method or to the component, and a scenario says which refusal the position gets;
  the position on the parameter of a compact constructor has its scenario too (the javadoc of `RecordRenameScenarios`
  says `prepareRename` declines it, which is no longer the first answer);
- `bin/ci` passes.

## Resolution

Closed without work (04-11-2026, decision of the user). The issue hardened the record check of `d414620`, a pre-flight
test of every rename (`definition` and `semanticTokens/full`) that decided for jdtls whether the symbol is a record
component. That check fought jdtls instead of relaying it, and `d414620` ([pull request
12](https://github.com/NolwennD/gabier/pull/12)) is closed unmerged. What jdtls 1.63.0 does is a gap of its own: its
fork of `RenameSupport` (`corext/refactoring/rename/RenameSupport.java`) has no case for a record, and the redirection
that JDT UI added for it (Eclipse bug [566507](https://bugs.eclipse.org/bugs/show_bug.cgi?id=566507)) was never ported;
no setting or client capability changes that. gabier now relays the failure and documents the limit
(`rename-a-record-component`), and the fix is tracked upstream (`jdtls-renames-a-record-component`).

Tokens: none; no agent worked on this issue.
