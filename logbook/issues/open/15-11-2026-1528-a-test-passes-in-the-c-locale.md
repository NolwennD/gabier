# The tests pass in a JVM that has no locale

Opened: 15-11-2026 15:28

`TypeNameTest.acceptsTheNameOfAJavaIdentifier` (`modules/core`, the `Café.java` case) fails when the JVM runs in the C/POSIX
locale (`LC_ALL=C`, or no `LANG`): `InvalidPathException: Malformed input or input contains unmappable characters:
src/Caf?.java`. It passes in a UTF-8 locale. Found while measuring the test JVM (ticket 14-11-2026-0125). It matters now
because a test JVM started by the daemon (the command that runs the affected tests) has the environment of the daemon, which
a session with no `LANG` leaves without a locale.

Done when:
- `TypeNameTest` passes with `LC_ALL=C` and with a UTF-8 locale, with the cause fixed (the test or the code that builds the
  path, whichever is wrong), not skipped.
- A test (or the same one) shows that the check of a type name does not depend on the locale of the JVM.
- Any other test of the repository that fails under `LC_ALL=C` is listed in the resolution, with the run that shows it.
