---
name: extend-guide
description: Use when a situation was met while working on Java (a problem an agent had with a piece of code, a clean-up or an action that solved it, a move that jdtls does not offer) and the guide `docs/improve.md` (`gabier help guide`) has no entry for it. Also to correct an entry that a new jdtls or a new gabier command made wrong.
argument-hint: '<the situation, in the words of the person who had it>'
---

# Add an entry to the guide of the improvements

`docs/improve.md` is the one source of the guide. The build copies it into the resources of the cli, next to the class `GuideTopic` (`eu/gabier/cli/infrastructure/primary`, the primary adapter that reads the resource and
names the topic `guide` of `gabier help`; `:cli:copyGuide`,
`modules/cli/build.gradle.kts`; kept by the native image in `modules/cli/src/main/resources/META-INF/native-image/
eu.gabier/cli/resource-config.json`), and `gabier help guide` prints it with no clone. Below the command line, the guide
is the domain value `Guide` (`eu.gabier.cli.domain`: the text, and its `GuideSection`s, read by `GuideSections`), ranked
by the `ranking` package (`eu.gabier.cli.domain.ranking`), and a question is answered by `AskTheGuide.answer(task,
guide)` (`eu.gabier.cli.application`), which another front end calls too; `HelpText` only writes the answer. A page of the guide per
situation: its title is the situation, never the name of a tool. A situation met with no entry becomes an entry.

## Steps

1. **Say the situation.** One sentence, in the words of the person who had the problem ("a string built with calls and
   quotes", not "String.format proposal"). Search the guide first (`grep -n -i "<word>" docs/improve.md`): an entry that
   exists is corrected, not duplicated. The guide holds situations; a command's usage stays in `docs/commands/`.
2. **Observe before writing.** Run what you are going to recommend, on real code, and read the answer:
   - the language: `gabier cleanup --dry-run <file> <id>` for a clean-up of the whole file (`gabier help cleanup`), or
     `gabier actions <file> <line>:<column> <line>:<column>` on the place, then `gabier apply-action --dry-run <file>
     <range> "<title>"`; the code of the before and after is the diff you saw, shortened, never imagined;
   - a refactoring: the command that `gabier actions` names with `-> gabier <command>`, run with `--dry-run`;
   - a move jdtls does not offer: `gabier actions` lists nothing for it on the place, **and** the source of jdtls shows
     why (its tag is the `jdtls_version` of `mise.toml`; ask `ripwire <clone of eclipse.jdt.ls> --for="<question>"`,
     read the file it points to, and never write the path of the clone in a file of this repository). The entry says
     "by hand" and the reason. A clean-up that rewrote nothing on the shapes tried says so; look in the
     source of the clean-up (the `.source` jar of jdt.core.manipulation) for the shape it does rewrite before giving up.
3. **Write the entry** in the section that fits (situations met on gabier; the language, clean-ups; the language,
   one place; the structure; what jdtls does not offer), as a `###` section with these lines, each starting a
   line, in this order:
   - `Words:` one line, plain English, what a person says about the problem. `gabier help --for="<task>"` ranks the
     sections by BM25 over their title, this line (which weighs 3 against 2 for the title and 1 for the rest) and
     their text, with no stemming: write the forms a person types ("concatenation", not only "concat") and a phrase for the move the
     entry is the answer to ("turn a string concatenation into a text block"). The line is one line of 120 columns at
     most (the next line is read as text, at the weight of the body). A section answers only when the question has
     **two distinctive words** (held in the title or the `Words:` line of about a third of the sections at most; "this",
     "that", "these" and "those" are left out of the question) in its title or this line, so the words of the situation
     go there, not in the text, and the name of the task as an agent types it ("organize imports", "show errors") is there too;
   - `Ask:` the command or the action, or `By hand:` when gabier has none (exactly one of the two);
   - `Before:` and `After:` with a fenced `java` block each, when there is code;
   - `Limits:` one paragraph: the Java release it needs when there is one, then when it does not work and
     what to do then.
   Lines stop at 120 columns. A clean-up has its identifier in its `Ask:` line as
   `` Ask: `gabier cleanup --dry-run <file> <id>` ``.
4. **Run the test of the guide**: `./gradlew :cli:test --tests '*GuideTest' --tests '*HelpTest'`, read to the end
   (`GuideTest` checks the lines of every section and that each clean-up of `gabier help cleanup` has its section). A
   situation that must always stay covered (a case met on gabier) gets a line in `GuideTest`. Add the question that
   leads to the entry to the table of `GuideQuestionsTest` (`./gradlew :cli:test --tests '*GuideQuestionsTest'`): it
   must rank the entry first, and the entries around it must keep their questions. If it does not, change the `Words:`
   line, not the weights.
5. **Check the printed guide** with the native cli, run from a folder with no clone: `bin/ci --stage native` does
   (`help guide`, `help --for=...`), and the binary is `modules/cli/build/native/nativeCompile/gabier`.
6. **One entry serves the command line and MCP.** The `guide` tool of `gabier mcp` ranks the same sections
   (`AskTheGuide`) and writes them with the same text (`GuideText`) as `gabier help --for`, with `gabier <command>` turned
   into the name of the tool, `gabier <command> --token` into `<command>-token` and each `--option` into the parameter
   `option=true` (`ToolWords`, only for a command that is a tool). So an entry names its commands as
   `` `gabier <command> ...` `` and nothing else (no `gabier help ...` in an `Ask:` or `By hand:` line, where there is
   no tool to turn it into), and writes an option as a flag that is a boolean parameter of the tool
   (`--dry-run`, `--include-declaration`); `GuideThroughMcpTest` fails on an entry that names a command that is no tool,
   on a command or an `--option` left in the answer of any section, and on a `name=true` that is no boolean parameter
   of a tool.
7. A new clean-up in `CleanUp` (core) is listed in the notes of `Cleanup` (cli), and `GuideTest` reads its identifiers
   from those notes: it fails until the guide has a section whose `Ask:` line names the new one.

## Applied once (04-11-2026)

The first application added "A boolean variable that reads the wrong way" (`Invert local variable`, observed with
`gabier apply-action --dry-run` on a scratch project), which the entry "A condition to turn round" only named in
passing.
