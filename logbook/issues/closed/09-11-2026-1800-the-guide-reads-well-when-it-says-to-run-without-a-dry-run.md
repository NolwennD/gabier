# The `guide` tool reads well when it says to run again without a dry run

Opened: 09-11-2026 18:00

Friction met while trying `gabier mcp`: the answer of the `guide` tool for "rename a method" ends "then without
`dryRun=true`, and `diagnostics <file>`". The rewrite of `--dry-run` into `dryRun=true` (`mcp/ToolWords`) turns every "then
without `--dry-run`" of the guide into a sentence an agent has to puzzle out. The entries are in `docs/improve.md`
(rename, organize-imports, move, create, and any other that has the phrase); `gabier help --for` must stay as it is.

Done when:
- every answer of the `guide` tool that tells to apply a change after a dry run says it plainly, for instance "then
  call it again without `dryRun`", and no answer holds "without `dryRun=true`";
- a test over every section of the guide (as `GuideThroughMcpTest` does) fails on that phrase;
- `gabier help --for` and `gabier help guide` print what they printed before (compare before and after);
- `bin/ci` is green.

## Resolution

Commit f90ab06 (PR #38). `mcp/ToolWords` rewrites "then without `--<option>`" into "then call it again without `<option>`" for any option, before the rewrite of an option into `name=true`; `docs/improve.md` is untouched. The answer of the `guide` tool for "rename a method" now reads "then call it again without `dryRun`, and `diagnostics <file>`".

- Red first: `ToolWordsTest.saysToCallAgainWithoutTheParameterWhereTheTextSaysThenWithoutTheOption` and `...WhateverTheOption`, and `GuideThroughMcpTest.saysToCallAgainWithoutDryRunWhereTheGuideSaysThenWithoutIt` over every section of the guide (no answer holds "without `dryRun=true`", some hold "then call it again without `dryRun`"). The existing `ToolWordsTest.turnsAnOptionIntoTheBooleanParameter...` encoded the phrase that the issue removes: its sample now reads "then `--dry-run`" (a changed expectation, not a weaker assertion).
- `gabier help guide` and `help --for` for the 43 sections: 44 outputs compared before and after with `diff -r`, identical.
- `bin/ci` of the worker on 72ff66d, the last commit before the branch was rebased over a README commit (a `.md` change, so no new run by the worker): format, build, test, native, jdtls passed; release-install skipped (no changed file reaches `install.sh`). Check `bin-ci` of PR #38 green on the rebased branch (10m8s; 11m41s on the first push).
- Not reviewed: small ticket (13 lines of production code, a rewrite of the text of the guide tool; the rest is test code).

Tokens:
- worker: 181,045 (its completion notification).
- reviewer: none (not reviewed).
- supervisor: 22 calls, output 14,391, input + cache write 35,637 (cache reads 5,798,003), covering the supervisor session from the launch of the worker (17:00 UTC) to the merge, other work included (the README, the draft of the notes of the release, the pull request); exchanges with the user: 0 messages.
