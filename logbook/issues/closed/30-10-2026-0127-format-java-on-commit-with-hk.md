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

## Resolution

Done in efe6114, by a subagent, then checked by the supervisor and reviewed twice by a neutral
reviewer (no blocking finding). `hk.pkl` runs open-java-format 2.98.0.6 on the staged `*.java`
files; hk 2.9.0 and the formatter are pinned in `mise.toml`; `mise install` also installs the hook
(`postinstall = "hk install --mise"`). The three existing Java files are reformatted (4 spaces, 120
columns) and a `.editorconfig` mirrors that style.

Checked: a badly formatted staged file comes out formatted and restaged; `hk check --all` passes;
a second `hk fix --all` changes nothing; it works from a ticket worktree; the formatter reads
`module-info.java`, records, sealed types, record patterns and unnamed variables; `pkl` is not needed.

To remember:
- the git hook itself lives in `.git/hooks` and is not versioned: run `mise install` once after
  cloning (in a new worktree, `mise trust` first);
- the hk version is written in two places, `mise.toml` and the `hk.pkl` amends URL;
- if another pre-commit hook already exists, hk refuses to overwrite it and mise only prints a
  warning: the formatter is then not active;
- open suggestions, not done: say so in the README, and add `trim_trailing_whitespace` and
  `continuation_indent_size = 8` to the `.editorconfig` if editors should match the formatter.
