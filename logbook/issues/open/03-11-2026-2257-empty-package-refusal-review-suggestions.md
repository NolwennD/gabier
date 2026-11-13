# The refusal of an empty package says the right thing on every segment

Opened: 03-11-2026 22:57
On hold: findings of the review of `rename-package-refusal-says-the-symbol-exists`, no `Done when` point broken and no use met yet.

Done when:
- on the last segment (`JdtlsLanguageServer.rename`, the position is on the whole name of the file's package) an empty
  edit is not refused as "has no class of its own ... put the position on its last segment", which is false and circular;
  it is refused with its own message or as before, and the two rows `15,legacy.tools` and `19,legacy.tools` of
  `JdtlsLanguageServerTest` say so;
- the dot between two segments: `package legacy.tools;` at column 14 has a row in the test, observed on the real jdtls
  first (which segment does jdtls rename?);
- the scenario `rename-a-package-that-holds-no-class` uses a new name that is a valid prefix rename, and the message of
  `LanguageServerException.emptyPackage` and `gabier help rename` say the same thing about the new name;
- `bin/ci` passes.
