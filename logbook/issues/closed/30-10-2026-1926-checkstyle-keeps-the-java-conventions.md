# Checkstyle keeps the Java conventions

Opened: 30-10-2026 19:26

The formatter fixes the layout (open-java-format, 120 columns), but nothing checks the conventions it cannot: names,
unused imports, Javadoc, modifier order, a field declared after a method, and so on. Checkstyle holds them, as a Gradle
task of `check`, on the main and test code of every module, configured once in `gabier.java-module-conventions`.

Done when:
- one configuration file (the rules of the project, the Google or Sun style trimmed to what the formatter does not
  already enforce, with the reason for each rule left out) and a task wired into `check` and `bin/ci`;
- an offence (a badly named constant, shown once, then removed) fails `check`; the existing code passes, the offences it
  has today are fixed or a rule is dropped with its reason;
- a new third-party jar follows the declaration of the others (catalog, `gradle/modules.properties`), and Checkstyle
  runs on the module path as the rest of the build does;
- `.claude/rules/quality.md` lists the gate; `bin/ci` green.

Depends on: 30-10-2026-1511-unused-code-fails-the-compilation (both edit `gabier.java-module-conventions` and the code of
every module).

## Resolution

Merged as `8d274c7` (rebased once over rules-only commits on `main`, so `bin/ci` was not run again).

Checked by the worker's `bin/ci` (full, on the worktree): format 4s, build 3s, test 64s (Checkstyle runs inside it),
native 120s, jdtls 116s; test counts cli 46, core 254, daemon 245, protocol 218, shared 236, architecture 18, no
failure. `config/checkstyle/checkstyle.xml` is the Google style of Checkstyle without its layout rules (the formatter's),
with `DeclarationOrder`, `UnusedImports`, `TodoComment` (bans TODO, FIXME, XXX, as the workflow rule says) and a few
bug-prone checks added; its header says, rule by rule, what is left out and why. `suppressions.xml` holds the one
suppression: the seed4j copy `shared/error/domain/Assert.java` is exempted from `SummaryJavadoc` and
`JavadocParagraph` only (95 offences, the only file of `shared` that offends). The plugin `gabier.checkstyle-conventions`
hangs the tasks for main, test, integrationTest and testFixtures off `check`, in every module and in `:architecture`.
Scratch proof: `static final int bad_name = 1` made `:protocol:checkstyleMain` fail with `[ConstantName]`, then was
deleted. Existing offences fixed without behaviour change: 2 constant names, 3 star imports, 7 declaration orders, 3
overload groupings, 10 missing Javadoc summaries.

Deviation from the `Done when` points, decided by the supervisor: the third point asked for Checkstyle to be declared like
the other jars (catalog, `gradle/modules.properties`) and to run on the module path. Checkstyle is a build tool, as Error
Prone is: it runs in Gradle's own `checkstyle` configuration and never reaches a module path, so its version is a plain
entry of the catalog (`checkstyle = "14.3.0"`) and `modules.properties` has no line for it. `extraJavaModuleInfo` did not
touch it, and the `selfCheck` stage still passes.

Review (one pass): one BLOCKING finding, the deviation above, which is a wording of the issue and not a defect; it is
stated here. Suggestions: the wording and line length fixed by the supervisor in `569a0ca` (a `@param order` that is the
order of the tags, the report paths of `verify`, a Javadoc line of 128 columns in `bin/ci.d/30-test`, the scope of the
gate: `build-logic` and the `bin/ci.d` scripts are not covered). Left: `VariableDeclarationUsageDistance` stays dropped
for three deliberate orders (`PlanBuilder.rename` reads the source before it checks the target; two tests start a
fixture before the steps they prove); the reviewer would keep the rule and annotate the three sites. Not decided here.

Tokens:
- worker: 126242 (its last completion notification; it was resumed by messages)
- reviewer: 67755
- supervisor: 20956 output, 30369 input + cache write over 17 calls (cache read 4144113), supervisor session over the
  period of the ticket (from the launch of the worker; the other work of the session included); exchanges with the user:
  1 message
