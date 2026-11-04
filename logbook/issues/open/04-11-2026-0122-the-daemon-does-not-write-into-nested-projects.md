# the daemon does not write into the nested projects of the repository it serves

Opened: 04-11-2026 01:22

gabier changes only what a command was asked to change, and it left a repository dirty without being asked. In
eclipse.jdt.ls (a Tycho repository with test projects of every kind under `org.eclipse.jdt.ls.tests/projects/`), the first
call started the daemon, which took 3 minutes 23 seconds while jdtls imported the root and the nested projects. From a
clean tree, `git status` then showed 8 tracked files modified (`.project`, `.classpath`,
`.settings/org.eclipse.jdt.core.prefs`) and 136 untracked entries (`.settings`, `.project`, `.classpath`, `.gradle`,
`build`) under that folder. The files were written during that first call, minutes after a Maven build had ended, which
points to the import; to confirm by reproducing on a fixture, as the first point says.

Done when:
- [ ] Red first: on a fixture with a nested Gradle project and a nested Eclipse project, `gabier actions` on a file leaves
      `git status` of the fixture unchanged (today it does not, to be confirmed).
- [ ] The daemon either imports only the project it serves, or keeps what the import writes out of the working tree; the
      choice, and what it means for the references of a symbol in a nested project, are in `docs/daemon.md`. `bin/ci`
      passes.
