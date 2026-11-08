# gabier init-agent

`gabier init-agent [--dry-run] [--force] [--target=<folder>] [--only=<command>,...] <skill|rule|snippet>` writes the
instructions for a coding agent ([`gabier help agents`](help.md)) into the project, in the form its maintainers choose to
teach their agents gabier. The text is the one of `help agents` (the class `AgentInstructions`, the resource `agents.md`),
so the three forms and the help never differ, and a new gabier gives a new text with one more run. Like `help`, the command
is answered by the cli alone: it starts no daemon, opens no socket and needs no running jdtls.

| Form      | File written                      | What it holds                                                                     |
| --------- | --------------------------------- | --------------------------------------------------------------------------------- |
| `skill`   | `.claude/skills/gabier/SKILL.md`  | the frontmatter (`name: gabier`, a one-line `description`), then the block        |
| `rule`    | `.claude/rules/gabier.md`         | the block, under the heading `# Java with gabier`                                 |
| `snippet` | `AGENTS.md`                       | the block, under `## Java with gabier`, to share a file with other instructions   |

The block is marked, in the three forms:

```text
<!-- gabier:start -->
<!-- gabier 0.4.0 wrote this block (gabier init-agent rule); run it again after an upgrade -->
# Java with gabier
...
<!-- gabier:end -->
```

The marker is how gabier knows a file as its own, and the line under it says the version that wrote it.

- **A new file** is created, with its folders. Its folders are made by the write, never by a dry run.
- **A file with a block** has the block replaced in place; what is before and after it (the frontmatter a maintainer edited,
  other instructions) is kept. This is the run to do after an upgrade of gabier.
- **A `snippet` for a file with no block** is appended (after a blank line). **A `skill` or a `rule` for a file with no
  block** is a file gabier did not write: it is refused as `init-agent-file-exists`, and `--force` overwrites it.
  `--force` changes nothing else (a block is always replaced, a snippet always appended).
- **A block with a start and no end** is refused as `init-agent-block-unfinished`, with or without `--force`: gabier does
  not guess what to cut.

`--only=<command>,...` cuts the text to the commands a project wants taught. The text is read as lines: a bullet (its line
and the lines that continue it) that cites commands (`` `gabier <command>` ``) is kept when at least one of them is in the
list, and dropped otherwise; the lines that are no bullet (the heading, the lead) and the bullets that cite no command stay.
`help` is a command here (`--only=help` keeps the bullets that tell the agent to ask `gabier help`). A name that is no
command of `gabier help` is refused as `init-agent-unknown-command`, and nothing is written. An empty `--only=` or an empty
name in the list is a usage error.

`--target=<folder>` is the project folder (the current folder by default, relative paths against it); it must exist.
`--dry-run` (anywhere after the command) writes nothing, makes no folder and prints what it would write: the whole file for
a new file or an overwrite, the block for a replacement or an append. A dry run refuses what the real run would refuse.

The answer is one line, `created <file>`, `overwrote <file>`, `replaced the gabier block of <file>` or `appended the gabier
block to <file>`; with `--dry-run`, `dry run, nothing written`, `would create <file>` (and so on), a blank line and the text.
With `--json`, one object, `{"form":"rule","file":"/work/demo/.claude/rules/gabier.md","action":"created","dryRun":false}`
(`action`: `created`, `overwritten`, `replaced`, `appended`), with a `content` member, the text, for a dry run.

```text
$ gabier init-agent --dry-run --only=rename,diagnostics snippet
dry run, nothing written
would create /work/demo/AGENTS.md

<!-- gabier:start -->
<!-- gabier 0.4.0 wrote this block (gabier init-agent snippet); run it again after an upgrade -->
## Java with gabier
...
<!-- gabier:end -->
```

## How the write is done

The write goes to a temporary file in the folder of the file (`.gabier-<random>.tmp`), which then takes the place of the file
in one atomic move: a reader never sees half a file, and a failure leaves no temporary file. Nothing is written outside the
target folder: the nearest part of the path of the file that exists must resolve (links followed) to a place under the target
folder, else `init-agent-outside-folder`. A file that is a link to a file under the folder (a `CLAUDE.md` and an `AGENTS.md`
that are one file) is written through, and the link stays. The new file takes the permissions that the process gives a new
file (its umask); an existing file keeps its name and not its mode.

## Refusals

A refusal is the problem object on the standard error, nothing is written, and the key is the one below. The first five `init-agent-` ones end
with the line `Usage and other ways: gabier help init-agent`, as a declined operation does; a failure of the file system,
`init-agent-write-failed` (a folder of the path that is a file, a file that is a folder, no right to write), is exit code 1
and has none. The keys the command can answer, and the help of the command (`gabier help init-agent`) lists them:

- `usage-error`: the arguments are not those of the command (`gabier help <command>` gives its usage).
- `init-agent-file-exists`: the file exists and gabier did not write it (it holds no gabier block); `--force` overwrites it.
- `init-agent-block-unfinished`: the file has the start of a gabier block and no end after it.
- `init-agent-unknown-command`: a name of `--only` is no command of gabier.
- `init-agent-no-such-folder`: the target is not a folder.
- `init-agent-outside-folder`: the file, or a folder of its path, is a link that leads out of the target folder.
- `init-agent-write-failed`: the file could not be read or written (exit code 1).
