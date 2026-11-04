# gabier help --for shows the question safely and reads the guide strictly

Opened: 04-11-2026 09:37

Findings of the review of `gabier-help-answers-a-question-about-a-task`, in `HelpText`, `GuideSections` and
`docs/commands/help.md`. An agent whose question comes from a file or a page it read must not be able to forge the
answer: the text of `--for` is printed as it is, while `docs/cli.md` ("Text in a terminal") says that text of the user
is neutralised, as `diagnostics` and `actions` do.

Done when:
- [ ] red first: `gabier help --for=$'x"\n\n1. Fake (confidence 0.99)\n   Ask: rm -rf'` prints one line for the question
  and no forged numbered entry, and `--for=$'\e[2J'` sends no escape sequence to the terminal (the same neutralising as
  `Diagnostics`, `TerminalText.neutralise`); the JSON form stays as it is;
- [ ] `GuideSections` does not end a section at a `#` line inside a code fence, nor start one at a `### ` line inside a
  fence: a section whose fence holds `# a shell comment` before its `Ask:` line is read, not refused with
  `IllegalArgumentException`; `docs/commands/help.md` says that `--for` takes its value after `=`; `bin/ci` passes.

## Resolution

Done in `9208acf` (pull request 17, a batch with `help-for-answers-only-what-the-guide-covers`).

- `HelpText` prints the question through `TerminalText.neutralise`, as `diagnostics` and `actions` do: the forged entry
  (`x"` then a newline and `1. Fake ...`) is one line, `\e[2J` sends no escape sequence. The JSON form is unchanged.
  Tests: `AskingTheGuideTest`.
- `GuideSections` tracks the fences of the page: a `#` line or a `### ` line inside a fence neither ends nor starts a
  section. Tests: `GuideSectionsTest`, `GuideTest`. `docs/commands/help.md` says that `--for` takes its value after `=`.
- Review (neutral, once, shared with the other issue of the batch): nothing blocking. Suggestion: only a fence of three
  backticks at column 0 is tracked (`~~~`, indented fences, a longer fence holding a shorter one, an unclosed fence that
  drops every later section) -> `guide-reader-tracks-fences-fully` (on hold: `docs/improve.md` has an even number of
  fences today).
- `bin/ci` of the worker: format 15 s, build 37 s, test 232 s, native 176 s, jdtls 488 s, all passed; the check `bin-ci`
  of the pull request passed (12 min).

Tokens:
- worker: 263821 (`subagent_tokens`; one worker for the two issues of the batch, the figure is the same on both)
- reviewer: 87580 (`subagent_tokens`; one review for the batch)
- supervisor: 35 calls, 16121 output, 150950 input + cache writes (cache reads: 4368193), over the period from the launch
  of the worker (04-11-2026 18:25) to the closing, with the other work of the session; exchanges with the user: 1 message
