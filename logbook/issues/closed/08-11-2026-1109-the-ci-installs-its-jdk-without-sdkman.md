# The CI installs the JDK of the project without depending on the SDKMAN! service

Opened: 08-11-2026 11:09

Met on 08-11-2026 on the pull request of `a-refusal-points-to-help-for`: the check `bin-ci` failed three times in 36 to 39 s,
before any test, because the SDKMAN! installer got a 504 from its service (`curl: (22) … error: 504`, step "Install the
JDK of .sdkmanrc" of `.github/actions/setup-build/action.yml`). The check is required by the protection of `main` and
`release.yml` uses the same action, so a SDKMAN! outage blocks every merge and every release. The JDK stays the one
`.sdkmanrc` pins (`java=27.0.0.0+35-tem`): developers keep SDKMAN!, the runner no longer needs it.

Done when:
- [x] the action installs the pinned Temurin JDK without SDKMAN! (for instance `actions/setup-java`, pinned by commit like
      the other actions), reading the version from `.sdkmanrc`, never writing it a second time; `JAVA_HOME` and the `PATH`
      are those of that JDK, and the step fails when `$JAVA_HOME/bin/java` is not the pinned release;
- [x] `bin-ci` is green on the pull request of the change, all stages, native included;
- [x] the comments of the action, `AGENTS.md` and `.claude/skills/verify/SKILL.md` that name SDKMAN! for the runner say what
      the runner does now.

## Resolution

Done in 5a7ea99 (pull request 23, merged fast-forward). `.github/actions/setup-build/action.yml` installs the JDK with
`actions/setup-java` v6.2.0 (pinned by commit), `java-version-file: .sdkmanrc`: it reads the version and the distribution
(`27.0.0.0+35-tem`, Temurin) from the file, so the version is written once. The next step fails when `$JAVA_HOME/bin/java`
is not the release `.sdkmanrc` pins. The SDKMAN! installer is gone from the runner; developers keep SDKMAN!.

- Checked: `bin-ci` green on the pull request, all stages of `bin/ci --all` (run 42472527134, after six failed attempts
  caused by 504 answers of the download servers of GitHub, SDKMAN! and Gradle, none by the code). The log shows
  "Downloading Java 27.0.0+35 (Temurin-Hotspot)" and the check of the release passing.
- `AGENTS.md`, `README.md` and `.claude/skills/verify/SKILL.md` name SDKMAN! only for the machine of a developer (the
  JDK to select, the GraalVM that Gradle finds among its candidates): nothing to change there for the runner.
- Reverses: the JDK of the runner came from SDKMAN! (`sdk env install`); it now comes from the tool cache of the runner
  and GitHub (Temurin releases). The Temurin download is still a download from github.com, which is why the cache of
  `the-ci-caches-the-tools-it-downloads` follows.

Tokens:
- worker: none (the supervisor did this ticket itself, at the user's request: a workflow file that only the GitHub check can prove).
- reviewer: none (not reviewed: small ticket, workflow files only, no production code).
- supervisor: 136 calls, 88601 output tokens, 181907 input + cache write (21385301 cache reads, apart), from 11:09 to 12:44 on 08-11-2026
  (10:09 to 11:44 UTC) for this ticket and `the-ci-caches-the-tools-it-downloads`, with the review and the waiting for the checks of the two
  code pull requests of the day in the same period; exchanges with the user: 4 messages.
