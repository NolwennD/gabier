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

## Resolution

Commit 6afa2e2 (pull request 65, check `bin-ci` green in 20 min 28 s).

- **Mechanism**: the key `java.configuration.runtimes` of `.vscode/settings.json`, behind the interface `RuntimeSource`
  (one source today, `VsCodeRuntimes`; `RuntimeSources.STANDARD` is the one ordered list). Adding or removing a source is
  its class, its line in the list, its test and one sentence of the docs; the skill `add-runtime-source` says how.
- **Rejected**: `JAVA_HOME` or a mise lookup as a source. On the machine of the report `JAVA_HOME` is a JDK 27 for a project
  that asks for 25, and a mise or SDKMAN! lookup ties gabier to those tools. A new source can be added the day one is
  wanted.
- **Chosen**: a bad entry (missing folder, no JDK, another version, relative path, duplicate) is reported as a warning in the
  daemon log and in `gabier status`, and the draw of jdtls is used; failing the start was rejected, since the entry may
  concern an environment the project does not use.
- **Observed on the real jdtls 1.63.0, then scripted**: runtimes given in `initialize` bind from the first import;
  `didChangeConfiguration` rebinds at once; a missing path is ignored; a JDK 21 declared as `JavaSE-25` is bound without a
  complaint (hence the check with the `release` file).
- **Builder trace, two JDK 25 on the machine**: first start 1 full build; restart on the same choice 0; restart on another
  choice 1; the same again 0; a missing path 0.
- **Checked**: `bin/ci` of the worker: format, build, test (cli 1701, core 954, daemon 1445, protocol 457, shared 236,
  architecture 24, 0 failures), native pass; release-install and release-notes skipped; jdtls failed once on a Gradle
  daemon that disappeared, then passed alone (`JdtlsRuntimesReplayTest`, 4 tests).
- **Review**: no blocking finding. Suggestions: (1) the javadoc of `JdtlsRuntimes.overlay` and the declaration of a
  chosen JDK for an environment no project is bound to disagree, and (2) three conveniences used only by tests live in main
  code: opened on hold as `27-11-2026-1146-runtime-choice-review-suggestions`. (3) "Only the first entry of an environment
  counts" in `docs/daemon.md` was wrong for a bad first entry: fixed, "first valid entry". (4) The help of `status` names
  the source: left, `.claude/rules/help.md` justifies it.
- **Release notes**: this changes `gabier status` (a `jdks:` section) and what the daemon reads from `.vscode/settings.json`:
  `docs/releases/<version>.md` of the next tag must say it.

Tokens:
- worker: 353652
- reviewer: 89584
- supervisor: 30 calls, 17398 output, 320067 input + cache write (2676996 cache read), over the session from the launch of the
  worker to the merge, other work included; exchanges with the user: 1 message (a floor)
