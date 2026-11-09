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
