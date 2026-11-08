# gabier status says what jdtls is doing while it indexes the project

Opened: 08-11-2026 10:16

For 0.4.0 (an agent is not surprised by the time jdtls takes). An agent that runs `gabier status` while the daemon starts
or jdtls imports the project learns how far it is and whether it is waiting for a normal reason, instead of reading
"not ready". Today the first call waits 46 to 65 s with no word (`the-first-call-says-why-it-is-slow`), and 3 min on a
root with nested projects.

Done when:
- [ ] observed first on the real jdtls (a throwaway replay): which notifications it sends during the start and the import
      (`$/progress`, `language/status`, `language/progressReport`), with their content for this repository;
- [ ] the daemon keeps the last state of jdtls (starting, importing the project with its message, ready) and `gabier status`
      prints it with the time spent in it, in text and with `--json`; a daemon that is ready answers as before;
- [ ] a scenario (the state a status answers) scripted on the fake and replayed on the real jdtls, or the resolution says
      why it cannot be; `docs/commands/status-and-stop.md` and `gabier help status` list the states; `bin/ci` passes.

Depends on: the-first-call-says-why-it-is-slow (same module, same wait; its stderr line can read this state).
