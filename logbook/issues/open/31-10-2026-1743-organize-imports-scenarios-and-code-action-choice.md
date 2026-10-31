# The organize-imports scenarios cover the other shapes of edit

Opened: 31-10-2026 17:43
On hold: review suggestions that break no `Done when` point and no real use yet; lifted if one of these shapes is met
while using gabier (31-10-2026 refocus).

Found in the review of `gabier-organizes-the-imports-of-a-file`.

Done when:
- [ ] Two scenarios replayed on the real jdtls: a file with a `package` and no import that uses `List` (a zero-length
      insert), and a file whose only import is unused (the edit becomes empty text); `WorkspaceEdits` clamping and
      `ChangeApplier` meet them.
- [ ] `JdtlsLanguageServer` picks the code action by its kind (`source.organizeImports`), not the first that is not a
      command (`JdtlsLanguageServer.java:266-270`): a test offers two actions, one of another kind.
- [ ] `bin/ci` green, coverage 100%.
