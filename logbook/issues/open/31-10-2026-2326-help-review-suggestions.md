# Review suggestions of the help ticket

Opened: 31-10-2026 23:26
On hold: review suggestions that break no `Done when` point and no use met yet; lifted if one of them is met while using gabier
(31-10-2026 refocus).

Found in the review of `gabier-has-a-help`. All in the module `cli`, except the first.

Done when:
- [ ] `Json` (module `protocol`) exports only `quote`; `parse` stays package-private and the test of the help checks the one
      line and the escapes with string assertions (`Json.java:14,55`).
- [ ] `HelpText.java:45` no longer says that with `--json` every answer is JSON: `gabier --json --version` prints text; the line
      says what is true, with a test.
- [ ] `gabier help --dry-run` and `gabier help --help`, `help -h` have a defined answer (the help of `help`, or a usage error that
      says why), with a test (`Gabier.java:111-116`).
- [ ] `GabierTest`: the test `theListGivesOneLineAndTheProtocolVersionPointerToHelpOfACommand` is named for what it asserts, and its
      assertion `.contains("diagnostics ")` can fail by itself.
- [ ] `Gabier.execute` and `standalone` read flat (no `Optional.<Command>empty()` in a ternary, no nested `switch` with an empty
      `default`). `bin/ci` green, coverage 100%.
