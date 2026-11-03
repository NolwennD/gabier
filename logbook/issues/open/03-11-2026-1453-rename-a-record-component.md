# gabier rename tells what jdtls says when it fails on a record component

Opened: 03-11-2026 14:53

An agent that renames a component of a record (`gabier rename --dry-run
modules/core/src/main/java/eu/gabier/editing/domain/Location.java 14:29 path`, the component `file` of `record
Location(Path file, Position position)`) gets `internal-server-error`, with no description, and cannot tell what went
wrong. jdtls 1.63.0 fails there, and gabier does not detect it first (decision of the user, 04-11-2026: jdtls is not
fought, `.claude/rules/workflow.md`): the error of jdtls reaches the agent in the shape of every other refusal of the
server, and the limit is written down. The cause is in jdtls, not in gabier: its fork of `RenameSupport` has no case for
a record, and the redirection of JDT UI (Eclipse bug [566507](https://bugs.eclipse.org/bugs/show_bug.cgi?id=566507)) was
never ported (`jdtls-renames-a-record-component`). The log of the cause in the daemon is
`the-daemon-logs-the-cause-of-an-error`.

Done when:
- a rename that jdtls answers with the response error -32603 (a record with a compact constructor, such as `record
  Mark(String label, int rank) { Mark { ... } }`) is refused with a `language-server-` key and the message of jdtls, not
  with `internal-server-error`; the mapping is made for every request of jdtls, not for records; no check of records, no
  `definition` or `semanticTokens` request before a rename; a test with the fake and a scenario observed on the real
  jdtls first (the key is compared, not the message);
- `gabier help rename` and `docs/commands/rename.md` say what jdtls does on a component of a record: with a compact
  constructor it fails (the refusal above); without one it renames the component, its accessor calls and `this.file` and
  leaves the uses by simple name in the body, which no longer compiles (run `gabier diagnostics` after); a position on a
  call of the accessor is declined by `prepareRename`. They point to the upstream issue;
- the partial rename has no replay (`Effect` compiles the result, which does not compile): the `## Resolution` says so;
- `bin/ci` passes.
