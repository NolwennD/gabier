# gabier status says what jdtls is doing while it indexes the project

Opened: 08-11-2026 10:16

For 0.4.0 (an agent is not surprised by the time jdtls takes). An agent that runs `gabier status` while the daemon starts
or jdtls imports the project learns how far it is and whether it is waiting for a normal reason, instead of reading
"not ready". Today the first call waits 46 to 65 s with no word (`the-first-call-says-why-it-is-slow`), and 3 min on a
root with nested projects.

Done when:
- [x] observed first on the real jdtls (a throwaway replay): which notifications it sends during the start and the import
      (`$/progress`, `language/status`, `language/progressReport`), with their content for this repository;
- [x] the daemon keeps the last state of jdtls (starting, importing the project with its message, ready) and `gabier status`
      prints it with the time spent in it, in text and with `--json`; a daemon that is ready answers as before;
- [x] a scenario (the state a status answers) scripted on the fake and replayed on the real jdtls, or the resolution says
      why it cannot be; `docs/commands/status-and-stop.md` and `gabier help status` list the states; `bin/ci` passes.

Depends on: the-first-call-says-why-it-is-slow (same module, same wait; its stderr line can read this state).

## Resolution

Done in 9a574f5 (pull request 25, in the batch with `the-first-call-says-why-it-is-slow`). Observed first on the real
jdtls, two runs on this repository: `language/status` carries `Starting` messages all along (`Init…`, then
`0%/45% Starting Java Language Server`, then `70% … - <task>` during the Gradle import, `ProjectStatus: OK`, `Started: Ready`,
`ServiceReady`); the numbers in front run past 1000, so they are not percentages and are not read. With
`window.workDoneProgress` declared, jdtls also sent `window/workDoneProgress/create` and `$/progress` with the same content,
and `language/progressReport` was never sent; so only `language/status` is used, which needs no capability (the capability
change was reverted). The throwaway runs were deleted: this paragraph is their only record.

- `StartProgress` (daemon) keeps the state, fed by `JdtlsClient`; `JdtlsProgress` (protocol) is the phase `launching` or
  `importing`, the message and the time in the phase. `gabier status` prints `jdtls starting (importing the project for 47s:
  Configure project :core)`; `--json` has the member `jdtlsProgress`, present only while jdtls is starting. A ready daemon's
  JSON is byte-identical to before. Text from jdtls is neutralised in the text form and relayed exactly in JSON.
- Scenario: the `Scenarios` catalogue holds port answers (change sets, diagnostics) and this is the status of the daemon, not
  a port answer, so no entry there. The pair is the fake script in `JdtlsLanguageServerTest` and `StartProgressReplayTest`
  (`jdtlsProbe`): launching then importing with a non-blank message, then ready with no progress, without comparing the words
  of jdtls; `JdtlsCouplingTest` is unchanged. On a small Eclipse project the real jdtls went launching at 5.5 s and importing
  from 5.7 s.
- Docs: `docs/commands/status-and-stop.md` lists the states and the stages, `gabier help status` has a Notes section,
  `docs/daemon.md` and the README are updated. Checked as for the other ticket of the batch (worker's `bin/ci`, `bin-ci`
  green on pull request 25).

Tokens (the batch of `the-first-call-says-why-it-is-slow` and this ticket):
- worker: 603543 tokens (294031, then 309512 for the rebase on `main` after the pointer ticket).
- reviewer: 88514 tokens (1 notification).
- supervisor: 194 calls, 122936 output tokens, 248045 input + cache write (30770230 cache reads, apart), from 10:18 to 13:30
  on 08-11-2026 (09:18 to 12:30 UTC); the period is the one of the day, with the pointer ticket, the two CI tickets and the
  waits for the checks inside it, so the figure is not this batch's alone; exchanges with the user: 6 messages.
