# The probe checks the diagnostics, including a deliberate error

Opened: 30-10-2026 04:41

Extend the jdtls probe: open the `module-info.java` and the main class of each module, wait for the diagnostics jdtls
publishes for them, and fail if one is an error. jdtls compiles in the checkout with its own compiler, on the Java 25
modules of a Gradle build, so its diagnostics can differ from Gradle's: this is where a false error would show. As a
control, open an in-memory file with an obvious type error and expect jdtls to report it, otherwise a probe that sees
nothing would pass for nothing.

Done when:
- the probe fails with a readable list (file, line, message) when a real error is present, and passes on the clean
  project;
- every opened file received at least one publication of diagnostics, so that silence is not taken for success;
- the control error is detected;
- what jdtls says about this project is written in the resolution, false errors included: if there are any, stop and
  decide with the user between a newer jdtls, a jdtls-specific Gradle setting, or a list of tolerated errors.

Depends on: probe-fails-fast-when-jdtls-dies (both edit the probe).
