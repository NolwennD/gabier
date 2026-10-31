# The text diff of `gabier rename` keeps tabs and backslashes

Opened: 31-10-2026 16:04
On hold: a choice about `TerminalText.neutralise` (what the terminal may show); `--json` is exact, so no agent is blocked.

Found in the review of `gabier-renames-a-symbol`. The text output of `gabier rename` passes the diff through
`TerminalText.neutralise`: a tab shows as `\u0009` and each backslash is doubled (Java string escapes, the
`\ No newline at end of file` marker). The text diff of tab-indented or backslash-heavy source is then lossy.

Done when:
- [ ] The text diff shows tabs and backslashes as they are in the file, with control sequences still neutralised.
- [ ] A test with a tab-indented file and a Java string escape shows it; the README says how a diff is printed.
- [ ] `bin/ci` green, coverage 100%.
