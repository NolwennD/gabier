# Unused code fails the compilation

Opened: 30-10-2026 15:11
Depends on: 30-10-2026-1146-errors-reported-as-problem-objects (it touches the same modules)

JaCoCo finds code that no test runs, but only at the end of `check`. Error Prone finds unused code at compile time,
faster: its `UnusedMethod`, `UnusedVariable` and `UnusedParameters` checks only warn by default, and the copied
seed4j error package still has warnings.

Done when:
- [ ] The Error Prone checks for unused code are errors on the main code (in `gabier.java-module-conventions`, where
      NullAway is configured).
- [ ] The warnings they show today are fixed, the code copied from seed4j included, by removing the code or, with a
      reason, by an exception that says why.
- [ ] A test or a scratch change shows that an unused private method fails `compileJava` (shown once, then removed).
- [ ] `bin/ci` green.
