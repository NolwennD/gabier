# The jdtls probe also opens the test sources

Opened: 30-10-2026 12:31

The probe opens the `module-info.java` and the sources of `src/main/java` of each module, and nothing else. Error Prone
runs on `compileJava` only, so the test code, the fixtures and the integration tests are checked by javac alone, and
jdtls has already shown that it can see what javac accepts: it rejected the inferred generics of a `map` in a test of the
shared error package. Open the sources of `src/test`, `src/testFixtures` and `src/integrationTest` as well, by the same
rule (a publication per file, no error), so that what an agent edits in tests is covered too.

Done when:
- the probe opens the Java files of every test source set of the modules and of the `architecture` project, receives a
  publication for each, and fails with the usual list if one is an error;
- the real run reports no error on them (otherwise the list is the finding: stop and report, do not tolerate it);
- the time added to the probe is stated in the resolution.

Depends on: diagnostics-of-a-file-through-jdtls (the code of the probe moves to the adapter there).
