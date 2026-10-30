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
