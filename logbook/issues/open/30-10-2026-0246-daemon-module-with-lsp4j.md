# The daemon module, with LSP4J as a real module

Opened: 30-10-2026 02:46

Add the `daemon` module (Java 25, like the rest) that depends on Eclipse LSP4J and Gson, the only place where they live.
The LSP4J 1.0.0 jars are automatic modules (no `module-info`, no `Automatic-Module-Name`), so give them proper
descriptors with the `extra-java-module-info` plugin, and have the build fail on any other automatic module. A small
self-check serializes an LSP4J `Position` with Gson.

Done when:
- the self-check passes when the installed daemon is started on the module path (Gson reflection can fail there while
  passing on the classpath), and it fails if the generated LSP4J module is closed (try it, then put it back);
- `module-info` requires only our own modules plus LSP4J and Gson, and no other module sees an LSP4J type (the
  architecture tests are extended if they do not already say it);
- `bin/ci` passes, the coverage gate and the architecture tests included.

Depends on: native-cli-runs-standalone (both edit the version catalog and the module build files).
