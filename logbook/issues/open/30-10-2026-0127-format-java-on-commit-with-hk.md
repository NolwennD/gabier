# Format Java on commit, with open-java-format and hk

Opened: 30-10-2026 01:27

Format the Java files at commit time: [open-java-format](https://github.com/openjavaformat/open-java-format)
as the formatter (one style, 120 columns), run by a pre-commit hook managed with
[hk](https://github.com/jdx/hk). Add an `hk.pkl` with a step on the staged `*.java` files, pin both
tools through `mise.toml` so Renovate can bump them, and document `hk install` in the README. The
formatter can reformat code between two minor versions, so its version is pinned.
How the formatter is run (its command line from the GitHub releases, or its Gradle plugin) is left
to whoever does it; a hook should stay fast.

Done when:
- committing a badly formatted `.java` file gives a formatted file, or a failed commit with a clear
  message;
- the hook works from a ticket worktree, since tickets are done there (not yet verified for hk);
- the formatter reads the project's real code without error, `module-info.java` and the records
  included (Java 25 to 27 syntax is not confirmed by its README), and formatting twice changes
  nothing;
- all the existing Java files are formatted, in this ticket (Nolwenn's go-ahead), so the hook starts
  from a formatted tree.
