# A restart on a kept workspace rebuilds nothing, after an update of gabier too

Opened: 27-11-2026 10:10

Real bug met while using gabier 0.7.2 on a large Maven project (`java.release` 25). The agent got a kept
workspace ("kept from the previous run" in the daemon log) and still waited about 3 minutes for a full build: the
7234 files of the class folder of jdtls were all rewritten, 14 minutes of CPU, a thread dump in
`BatchImageBuilder.buildAll` (type inference of lambdas and generics). During the build jdtls answered neither
`java.project.getSettings` nor the file notifications (`Timed out after 300.0 seconds`, `waiting for jdtls to take in the
changes on disk of 6 files`). `docs/daemon.md` ("The JDK of the projects") says a restart does not declare the JDKs
again and builds nothing.

Observed order in that log, to confirm: `Setting <sdkman>/25.0.4-graal as 'JavaSE-25' environment` 09:49:54, `JVM Runtimes
changed` 09:50:43, class files written from 09:50:52, no `build jobs finished`. Not established: that the declaration
was sent again, why, and whether the state the workspace kept had been written by 0.7.1 and was not recognised by 0.7.2
(a guess, the state file was not read). Other figures from the same project, cold: 9 min 30 and 4 min 30 for the
import plus the build.

An agent that restarts gabier on a project it already opened, after a new gabier was installed, waits for jdtls to be
ready as for a restart with the same version, with no full build.

Done when:
- The cause is established on a real run, with the Java builder trace (`.claude/rules/dogfooding.md` and the closed issue
  `15-11-2026-1112-a-restart-reuses-the-jdtls-workspace` say how), and written in the resolution: what made the daemon
  declare the JDKs again, or made jdtls rebuild, on a kept workspace.
- A restart on a workspace kept by the previous version of gabier rebuilds nothing, proved by a test that starts from the
  state the previous version wrote (the fake server), and by the trace on a real jdtls.
- When the state of a kept workspace cannot be used, the log of the daemon says why, and `docs/daemon.md` stays true.
