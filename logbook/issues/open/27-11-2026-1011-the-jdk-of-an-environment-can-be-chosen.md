# The JDK that jdtls binds to an environment can be chosen

Opened: 27-11-2026 10:12

Friction met while using gabier 0.7.2 on a project that asks for `JavaSE-25` (`java.release` in the pom). The machine
holds several JDK 25 (SDKMAN! and mise), `JAVA_HOME` and the `current` of SDKMAN! are on a JDK 27. Nothing designates
the JDK of the project: jdtls draws one of the JDK 25 it finds (a GraalVM here), and gabier then declares that draw in
`java.configuration.runtimes` and keeps it. The user has no way to say "use the Temurin 25 of mise", and
`.vscode/settings.json` is read for the import preferences only (`JdtlsSettings`), so a `java.configuration.runtimes`
written there is ignored. A choice that changes between two starts is what rebuilds the project whole.

An agent, or the person it works for, can say which JDK an execution environment is bound to, and gabier uses it
instead of the draw of jdtls. The worker chooses the mechanism and says in its report why that one: the key
`java.configuration.runtimes` of `.vscode/settings.json`, the JDK of `JAVA_HOME` or of mise when it is of the version
the environment asks for, or another; the documentation of Eclipse JDT LS is read first (`.claude/rules/workflow.md`,
"jdtls is configured, not fought").

Done when:
- With several JDKs of the version of the project on the machine, the chosen one is the one jdtls binds, shown by
  `gabier status` and by the Java builder trace, at a first start and at a restart.
- A choice that names a JDK that does not exist, or of another version than the environment, is refused or reported with
  its cause, not silently replaced by the draw of jdtls.
- `docs/daemon.md` ("The JDK of the projects"), the help of the command that shows it and `gabier status` stay true.
