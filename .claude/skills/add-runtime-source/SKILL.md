---
name: add-runtime-source
description: Use to add a way of choosing the JDK of an execution environment (a variable, a file of the project, a tool of the machine) next to the setting java.configuration.runtimes of .vscode/settings.json, or to remove or reorder one.
argument-hint: '<the place the JDK is read from>'
---

# Add a way of choosing the JDK

A project asks for an execution environment (`JavaSE-25`) and jdtls binds it to one of the JDKs of that version it finds,
a draw that changes from one machine, and from one start, to the next. A **source** is one way of saying which JDK an
environment is bound to. The daemon knows its sources in one list, `RuntimeSources.STANDARD`
(`modules/daemon/src/main/java/eu/gabier/daemon`), in their order of precedence: for an environment the first source that
chooses it wins. The session, the state of the workspace, `status` and the docs do not name any source: one source is
one class, one line of the list and its tests. Read `docs/daemon.md` ("Choosing the JDK") first.

## Steps

1. **Say where the JDK is read from, and why that place.** A place the project owns (a file in it) or one the machine owns
   (a variable, a tool). A source reads its place and nothing else: no jdtls, no workspace, no other source.
2. **Test first, in the test class of the source** (`<Source>Test`, as `VsCodeRuntimesTest`): what it chooses for a place
   that holds a good value, nothing and no warning for a place that says nothing, and one warning with its cause (the path
   and what is wrong) for each value it cannot use. A choice is only a JDK that exists (`Jdk.at` reads its file `release`)
   **and** is of the version of its environment: jdtls binds any folder it is given, whatever its version, and ignores a
   folder that does not exist in silence, so the source checks both.
3. **Write the class**, `final class <Source> implements RuntimeSource`, package-private, in `eu.gabier.daemon`:
   `description()` says how `status` shows the choice (`chosen in .vscode/settings.json`: one short phrase that names the
   place), and `read(Path root)` returns `RuntimeSource.Found` (the JDKs, one for each environment, and the warnings).
4. **Put it in the list**: one line in `RuntimeSources.STANDARD`, at the place of its precedence. That is the only edit
   outside the class and its test. Do not touch `JdtlsSession`, `JdtlsWorkspace`, `JdtlsRuntimes`, `ProjectJdks` or
   `DaemonStatus`: `ProjectJdks.Binding.source` is the `description()` as it is.
5. **The order is tested once, on the list** (`RuntimeSourcesTest`, with sources of the test): do not add a test of the
   order for the new source; add a case only if the order of the list has a rule of its own.
6. **Replay it on the real jdtls** when what it chooses changes how jdtls binds (a new kind of place does not: the choice
   reaches jdtls as `java.configuration.runtimes`, which `JdtlsRuntimesReplayTest` replays once, run by
   `./gradlew jdtlsProbe`).
7. **The docs**: one sentence about the place in "Choosing the JDK" of `docs/daemon.md`, and one in the help of `status`
   (`Operations.STATUS`) if the agent has to know the place to use it (`.claude/rules/help.md`). Nothing else says it.
8. **Check**: `bin/ci --fast`, then the full `bin/ci` once at the end.

## Removing or reordering

Removing a source is deleting its line in `RuntimeSources.STANDARD`, its class and its test, and its sentence in the docs.
The tests of the other sources and of the session stay green, because they use sources of the test
(`JdtlsSettings.choosing`). Reordering is moving a line; the doc names no order but "the first wins".
