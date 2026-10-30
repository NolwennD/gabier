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

## Resolution

Done in 281e67f, by a subagent. The module `fr.craft.gabier.daemon` (Java 25) requires `fr.craft.gabier.protocol`,
`static fr.craft.gabier.shared`, `org.eclipse.lsp4j` and `com.google.gson`. The LSP4J 1.0.0 jars are automatic modules, so
the convention plugin gives them real, open descriptors with `extraJavaModuleInfo` (`failOnAutomaticModules` and
`failOnMissingModuleInfo` on, no `closeModule()`: Gson reflects into them). The block lives in the convention plugin so
that a later module needing a non-module jar reuses it; the Maven coordinates are in `gradle/modules.properties` and the
versions in the catalog, under the name of the Java module. The task `:daemon:selfCheck` runs the installed daemon on the
module path and is part of `check`: `SelfCheck` serializes `new Position(1, 2)` with Gson and checks that the daemon and
`Position` are in named modules, so it cannot pass on the class path. A new architecture rule keeps LSP4J types inside
`fr.craft.gabier.daemon..`.

Checked by the subagent, `bin/ci`: format, build, test and native passed, `jdtls` skipped (no `jdtlsProbe` task yet);
`daemon 22`, `cli 9`, `protocol 4`, `architecture 12`, no failure. Proofs: the installed daemon runs with a module path
and prints the named modules; the same program on the class path fails with status 1; with `closeModule()` added the
check fails with a Gson reflection error on `Position#line`; a throwaway class of the `cli` module using an LSP4J type
fails the architecture rule.

Neutral review, done once: nothing blocking. What became of each finding:
- the generated `org.eclipse.lsp4j.jsonrpc` descriptor lacks `requires java.logging` (its `RemoteEndpoint` fails to load
  on the module path), and the self-check only touches `Position`: fixed by the supervisor, without a ticket;
- the start script runs the `JAVA_HOME` or PATH java, not the Java 25 toolchain, so the self-check may run on another
  JDK than the build claims: fixed by the supervisor, without a ticket;
- two lines of `gradle/modules.properties` look unnecessary: tried by the supervisor, see the commit that follows;
- an unknown argument makes the daemon print its version and exit 0, where the cli prints a usage and returns 2, and
  `MainTest` locks it in: left, the daemon's `main` is a placeholder and will take other arguments;
- `SelfCheckTest` on this runtime compares `(null, null)` with itself, and the record component `position` holds a module
  name: left, the real proof is the `selfCheck` task;
- the architecture rule covers LSP4J and not Gson, and the list of non-module jars is a mechanism meant to grow that
  ships its documentation in `quality.md` but no skill: left, to decide.

To remember: `requires transitive com.google.gson` on the two LSP4J descriptors was suggested because `GsonBuilder` is
part of the LSP4J API; it is not needed yet; the first code that uses JSON-RPC will show.
