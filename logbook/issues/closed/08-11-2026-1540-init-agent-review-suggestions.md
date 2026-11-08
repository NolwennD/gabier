# gabier init-agent keeps the mode, the line endings and the other blocks of the file it writes into (review suggestions)

Opened: 08-11-2026 15:40
Lifted on 08-11-2026 by the user ("corrige le problème init-agent").

- `AgentFile.java:109`: `ATOMIC_MOVE` of a fresh temp file gives the destination the umask mode: `chmod 600 AGENTS.md` then `init-agent snippet` loses the mode (a hard link is broken too). The docs say so; no test covers it. Copy the POSIX permissions of the existing file onto the temp file.
- `AgentFile.java:75`: only the first `START` is handled: a file with two blocks has the first replaced and the second left stale; a marker quoted in a code fence (an `AGENTS.md` that documents the markers) is replaced as if it were the block. Refuse when `START` occurs more than once, or skip fenced markers.
- `AgentFile.java:86`, `AgentForm.java:65`: the block is joined with `\n`; a CRLF file gets mixed line endings on append and on replace (a BOM file is kept as is).
- `AgentFile.java:69-109`: between the `exists` check and `ATOMIC_MOVE` a file created by another process is clobbered by the `CREATED` case; `CREATE_NEW` or a hard-link publish would close it.
- `AgentFile.java:75-83`: a `SKILL.md` that holds a gabier block but lost its frontmatter (or was built from `snippet`) gets only its block replaced: a skill with no frontmatter that never loads.
- `InitAgentTest.java:163`: `aWriteLeavesNoTemporaryFileBehind` exercises success only; removing the `finally` of `AgentFile.java:107-111` leaves it green. Add a failing move (a read-only folder, a destination that is a non-empty directory).
- `InitAgentTest.java:174`, `:194`: a test parameterized over three forms returns at once for `snippet`; `forceOverwritesAFileThatGabierDidNotWrite` is false for `snippet` (appended).
- `AgentInstructions.java:47`: with `--only=rename` the lead bullet keeps `extract-method`, `move`, `inline` and `create`, because a bullet is kept whole if any command it cites is chosen: say so in `gabier help init-agent`, or split that bullet in `agents.md`.
- Missing tests: a dangling link `AGENTS.md` (replaced by a regular file, not written through); `--target` that is itself a link; `--dry-run` with a link that leads out.

Done when:
- [x] each point above is either fixed with a test, or answered in this issue's resolution with the reason; `bin/ci` passes.

## Resolution

Done in 4b1496a (pull request 30, merged fast-forward). The user lifted the hold ("corrige le problème init-agent"). All nine
bullets are fixed with a test, none is only answered:

1. Mode: `keepModeOf` copies the POSIX permissions of the existing file onto the temporary file before the move
   (`aWriteKeepsTheModeOfTheFileItReplacesTheBlockOf`, mode `rwxr-x---`; the first red was 644 against 600). A hard link to
   the file is still broken by the move (an in-place write would lose atomicity): said in `docs/commands/init-agent.md`.
2. Blocks and fences: a file with two blocks is refused with the new key `init-agent-block-repeated` (exit 2, pointer line,
   file untouched, the three forms, `--force` included). A marker is a line of its own, outside a CommonMark fence (up to
   three spaces, three or more backticks or tildes, closed only by the same character, at least as long, nothing else; a line
   indented by four opens nothing): six cases in `aMarkerInsideAFenceIsNotABlockWhateverTheFence`, plus the two of the first
   round. A file that ends inside a fence that never closes is refused for a `snippet` with the new key
   `init-agent-fence-unclosed` (a block appended there would sit inside the fence, and a second run would append another);
   a block found before such a fence is still replaced; a skill or a rule is not checked.
3. Line endings: a replacement takes the ending of the START line of the block; an append takes the first line of the file;
   a new file is `\n`; a BOM file is kept (`theBlockTakesTheLineEndingsOfItsOwnStartLine` and four more).
4. Race: a new file is published with a hard link of the temporary file (`aFileThatAppearsBetweenThePlanAndTheWriteIsNotClobbered`),
   a name taken stays `init-agent-file-exists`, with a message that holds for a snippet and for a file written by a second
   run (`fileAppeared`: "created while gabier was writing it; nothing was written, run the command again"). On a file
   system with no hard links (FAT, some SMB mounts) it falls back to `Files.copy`, which creates exclusively
   (`aNewFileIsCreatedOnAFileSystemWithNoHardLinks`, `aFileThatAppearsOnAFileSystemWithNoHardLinksIsNotClobberedEither`, through
   a `HardLink` seam). The dangling-link case still goes through the move, so the race stays open for that one case.
5. Frontmatter: repaired, not refused: a skill that holds a block and no frontmatter gets it back at the top (after a BOM
   when there is one; `aSkillWithABlockAndNoFrontmatterGetsTheFrontmatterBack` and the BOM pair).
6. No temporary file after a failure: `aMoveThatFailsLeavesNoTemporaryFileBehind` (a destination that is a non-empty
   directory); removing the `finally` turns it red.
7. Tests: the hollow parameterized case now runs `skill` and `rule` only; `forceOverwritesAFileThatGabierDidNotWrite` is
   split in two, with `forceChangesNothingForASnippetWhichIsAppendedAnyway`.
8. `--only` cuts per bullet: said in `gabier help init-agent` and on its page, with the lead bullet that keeps
   `extract-method`, `move`, `inline` and `create` as the example; `agents.md` is not split.
9. Link tests: dangling `AGENTS.md`, `--target` that is a link, `--dry-run` with a link that leads out (green at once: they
   describe what the command already did; each can fail when the behaviour is removed).

- Review (neutral, once): two findings showed a bullet was not met, and went back to the worker once: the hard link made
  creation fail where hard links are missing (bullet 4), and the fence test was too crude (bullet 2: indented fences,
  4-backtick fences, `~~~` inside a fence, an unclosed fence). Both are fixed in this commit (it was amended, on the
  worker's unpushed-by-it branch, then force-pushed with lease). Smaller points of the same review also done: the BOM, the
  line ending of the START line, the umask-proof mode test. Not done: a BOM glued to the START marker on line 1 is not seen
  as a block (the worker's note, not asked); the new `@ExcludeFromGeneratedCodeCoverage` of `keepModeOf` (a file system with
  no POSIX permissions cannot be had on the test machines; the reviewer notes a custom `FileSystemProvider` could test the
  branch). Neither became an issue: no real use is broken.
- Checked: the worker's full `bin/ci` in one run on the final tree (format 12 s, build 2 s, test 198 s, native 175 s, jdtls
  606 s passed; release-install skipped), and `bin-ci` green on the pull request (run 42498208474, 11 min 42 s). The red
  step was followed this time: first red `aWriteKeepsTheModeOfTheFileItReplacesTheBlockOf`; two pairs written two tests at a
  time before running; six tests were green at once because they describe existing behaviour (named in the worker's report).

Tokens:
- worker: 177279 tokens (138317 for the first run, then the resumed one for the two blocking findings; the last notification is
  the figure).
- reviewer: 74812 tokens (1 notification).
- supervisor: 25 calls, 23397 output tokens, 48366 input + cache write (8065447 cache reads, apart), from 16:02 to 17:19 on
  08-11-2026 (15:01 to 16:19 UTC); it holds the writing of the two issues of 0.5.0 and the read of the other project;
  exchanges with the user: 1 message.
