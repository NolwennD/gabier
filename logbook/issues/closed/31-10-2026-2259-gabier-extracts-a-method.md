# gabier extracts a method from statements

Opened: 31-10-2026 22:59

An agent turns a block of statements, or an expression, into a method with `gabier extract-method`, instead of cutting and
pasting text: the second of the `extract-*` commands of the Python driver, on the shape of `extract-variable`
(`docs/commands/extract-variable.md`). `gabier extract-method [--dry-run] <file> <line>:<column> <line>:<column> <name>`
(1-based, end excluded) extracts the selection into a method called `<name>`, with the parameters and the return value jdtls
works out, and replaces the selection by the call. The answer is the files changed and the unified diff, text and `--json`;
a refusal is a problem object, nothing written. Daemon method (skill `add-daemon-method`) and cli command.

**Care**: jdtls is configured, not fought (`.claude/rules/workflow.md`). Read the JDT LS source and documentation first
(`java/getRefactorEdit` with the command `extractMethod`; the clone is at /home/nolwenn/dev/projects/eclipse.jdt.ls, at the
tag pinned by gabier, read-only), and write in the resolution which settings and capabilities give the behaviour. jdtls
names the method itself and expects the client to rename it (as for the variable): reuse the way `extract-variable` applies the
name (an in-memory overlay, `textDocument/rename`, one folded edit) if the documentation shows no better way.

Done when:
- [x] `gabier extract-method <file> <start> <end> <name>` extracts statements, and an expression, into a method with the
      name given, on disk; `--dry-run` writes nothing; shown on the fake language server (`GabierTest`, daemon tests on a real
      socket) and with contract scenarios replayed on the real jdtls (skill `add-scenario`): statements that use a local
      variable (a parameter appears), statements that assign one (a return value appears), an expression.
- [x] The method carries the name the agent gave, not the one jdtls proposes (a test that fails if it does not).
- [x] A selection jdtls cannot extract (half a statement, a `return` of a branch), a name that is not a Java identifier and a
      file outside the project are refusals with a problem object, nothing written; each is a scenario replayed on the real
      jdtls. What happens with a name another method of the class already has is replayed too, and the resolution says which
      refusal comes back and who produces it (jdtls or a check of gabier).
- [x] The page `docs/commands/extract-method.md`, the line of the README list and the row of `docs/protocol.md` are added
      (skill `add-daemon-method`); `.claude/rules/dogfooding.md` names the command. `bin/ci` green, coverage 100%.

Coupled with `31-10-2026-2259-gabier-has-a-help.md` (both touch the usage text of `Gabier` and `UsageException`): they run in parallel on the
user's decision; the second one to be merged rebases over the first.

## Resolution

Merged as `5b65734` (rebased by the supervisor over `.md`-only commits of `main`, no new `bin/ci`). `gabier extract-method [--dry-run] <file> <line>:<column>
<line>:<column> <name>` extracts statements or an expression into a method with the name given (parameters and return value
as jdtls works them out); text and `--json`, refusals as problem objects, nothing written. New key
`language-server-not-extractable` (jdtls answers no edit, or an answer without an edit: half a statement, an `if` whose branch
returns).

How jdtls is configured (JDT LS v1.63.0, the local clone), no setting and no capability: `java/getRefactorEdit` with the command
`extractMethod` (`GetRefactorEditHandler`); `RefactorProposalUtility.getExtractMethodProposal` names the method itself
(`proposeMethodNameHeuristic`, `getUniqueMethodName`) and gives null for a covering node that is not an expression, a statement or
a block, so the name is applied by the same rename as for the variable (overlay, `textDocument/rename`, one folded edit);
`JdtlsSettings` and `JdtlsCapabilities` are unchanged. The extraction flow of `JdtlsLanguageServer` is shared with
`extract-variable` by a private enum `Extraction`; the messages of the variable are character-identical (the reviewer checked).

**A name another method of the class has is not refused by jdtls**: `RenameHandler` throws only at FATAL, and its rename leaves two
methods of the same signature. The refusal `invalid-name` is a check of gabier, `JdtlsLanguageServer.refuseASignatureInUse`: it
lists the methods of the overlay with `textDocument/documentSymbol` (flat `SymbolInformation` without the hierarchical
capability) and refuses when the class has the name with the parameters of the new method; the same name with other
parameters is an overload, accepted. The check is weak in nested classes: see the issue below.

Proofs (worker's full `bin/ci` on the rebased commit: format 7s, build 3s, test 108s, native 133s, jdtls 310s; no stage skipped;
cli 165, core 353, daemon 528, protocol 326, shared 236, architecture 19, 0 failures; coverage 100%, Checkstyle green; two earlier
runs failed and were fixed: formatting of `Gabier.java` after the rebase, the command lists of `GabierTest`):
- statements and an expression, on disk, dry run writes nothing: `ExtractMethodTest`, `ExtractMethodMethodTest`, `DaemonServerTest`
  (real socket), `GabierTest$ExtractingAMethod`, the native stage `extractMethodWithTheFakeServer`, and the scenarios
  `extract-a-method-that-uses-a-local-variable` (a parameter), `-that-assigns-a-local-variable` (a return value),
  `-from-an-expression`, replayed on the real jdtls;
- the agent's name: `JdtlsLanguageServerTest$ExtractingAMethod` (fails if the rename is skipped) and the replay;
- refusals, each a scenario replayed on the real jdtls: half a statement, an `if` whose branch returns (`not-extractable`), `1st`
  (`invalid-name`, produced by jdtls), a file outside the project, the server importing, and a name in use (`invalid-name`, produced
  by the check of gabier above).
Not red first in every case (worker's note). The project scenario files gained `Totals.java`; no existing answer changed.

Review (neutral reviewer, once): nothing blocking. Done by the supervisor: the README line moved after `extract-variable`, and the
doc sentence "all of these are replayed" narrowed (the overload is tested on the fake only). Findings, opened: the check in nested
classes and the fake's labels that the real server never gives, the overload scenario, a message that says "variable" on the method
path: issue `31-10-2026-2352-extract-method-name-check-is-exact.md` (a real use, not on hold); the message of jdtls dropped from the refusals: issue `31-10-2026-2352-extract-refusals-carry-the-jdtls-message.md` (on hold). The one-line
wrappers `extractVariable` and `extractMethod` of `Gabier.java` disappear in `gabier-is-composed-of-capabilities`.

Tokens:
- worker: 368824
- reviewer: 124325
- supervisor: 50 calls, 80630 output, 179352 input + cache write (cache reads 13605058), exchanges with the user: 1 message.
  Measured from the launch of the worker (21:59 UTC) to the merge; it includes the `help` ticket's merge and the whole planning of
  the refactoring of the cli with the user.
