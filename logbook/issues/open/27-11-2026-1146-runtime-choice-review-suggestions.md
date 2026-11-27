# The JDK choice declares what it says, and keeps test conveniences out of main code

Opened: 27-11-2026 11:46
On hold: findings of the review of `27-11-2026-1011-the-jdk-of-an-environment-can-be-chosen`, no real use shows them yet.

Hardening of the JDK choice (`JdtlsRuntimes.overlay`, `JdtlsSession`, `JdtlsSettings`, `DaemonStatus`).

Done when:
- The javadoc of `JdtlsRuntimes.overlay` and the code agree on a choice for an environment that no project is bound to
  (`.vscode/settings.json` lists a valid JavaSE-21, the projects bind JavaSE-25 only): either it is not declared to jdtls
  and a test shows it, or the comment says it is declared and a test shows that.
- `DaemonStatus` (the constructor with nine arguments), `JdtlsSettings.choosing` and the constructor of `JdtlsSettings`
  with six arguments are used by production code, or live in the test fixtures.
