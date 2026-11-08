# The CI installs the JDK of the project without depending on the SDKMAN! service

Opened: 08-11-2026 11:09

Met on 08-11-2026 on the pull request of `a-refusal-points-to-help-for`: the check `bin-ci` failed three times in 36 to 39 s,
before any test, because the SDKMAN! installer got a 504 from its service (`curl: (22) … error: 504`, step "Install the
JDK of .sdkmanrc" of `.github/actions/setup-build/action.yml`). The check is required by the protection of `main` and
`release.yml` uses the same action, so a SDKMAN! outage blocks every merge and every release. The JDK stays the one
`.sdkmanrc` pins (`java=27.0.0.0+35-tem`): developers keep SDKMAN!, the runner no longer needs it.

Done when:
- [ ] the action installs the pinned Temurin JDK without SDKMAN! (for instance `actions/setup-java`, pinned by commit like
      the other actions), reading the version from `.sdkmanrc`, never writing it a second time; `JAVA_HOME` and the `PATH`
      are those of that JDK, and the step fails when `$JAVA_HOME/bin/java` is not the pinned release;
- [ ] `bin-ci` is green on the pull request of the change, all stages, native included;
- [ ] the comments of the action, `AGENTS.md` and `.claude/skills/verify/SKILL.md` that name SDKMAN! for the runner say what
      the runner does now.
