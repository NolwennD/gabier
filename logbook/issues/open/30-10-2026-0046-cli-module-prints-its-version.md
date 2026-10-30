# The cli module prints its version

Opened: 30-10-2026 00:46

Add the `cli` module (Java 25, JDK-only) that depends on `protocol` and answers `gabier --version`
with the protocol version. An unknown argument prints a short usage and fails.

Done when:
- the unit tests for `--version` and for an unknown argument pass;
- the module's `module-info` requires nothing but `protocol`.

Depends on: gradle-skeleton-and-protocol-module, architecture-tests-for-a-pure-java-core (the architecture tests
cover this module too, and both edit the build files).
