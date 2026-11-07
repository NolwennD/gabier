# gabier references refuses a symbol that does not resolve

Opened: 02-11-2026 19:34

Met while diagnosing `move-rewrites-references-in-other-modules`: `gabier references <file> 6:35` on `ErrorKey` in
`implements ErrorKey`, in a file of `modules/protocol` whose project did not see `shared`, answered with exit 0 the
references of the enclosing type `ProtocolErrorKey`. The agent takes them for those of `ErrorKey`. A position on a name
that does not resolve is refused with a key that says so, never answered for another symbol.

Done when:
- `references` at the position of a type name that does not resolve is refused (key in `gabier help references`), and a
  position between symbols still answers as today; a scenario and its replay show it;
- `bin/ci` passes.

## Resolution

Done in 6dd48bb.

- No code change: every unresolved name tried on the real jdtls (a missing import, a package not exported, a module not
  required, a package that does not exist) is already refused as `language-server-no-such-symbol` by the
  `prepareRename` gate. Red: with the gate removed temporarily, `references` at `ErrorKey` in `implements ErrorKey`
  answered the references of the enclosing enum, as in the issue.
- The scenario `references-of-a-type-that-does-not-resolve` (diagnostics project, `Unresolved.java`) is answered
  `Refused(NO_SUCH_SYMBOL)` and replayed; "a position between symbols still answers" is the existing
  `references-where-there-is-no-symbol`. Key kept: jdtls declines the same way for an unresolved name and a blank, so a
  distinct key would be a guess.
- Limit documented: a fully qualified unresolved name (`foo.bar.Third`, at `Third`) is not refused by jdtls and answers
  "no references".
- The review notes that `references.md` claims more than the scenario replays (only a missing package is replayed), and
  that the scenario locks an existing behaviour, so it was never red: `move-and-references-review-suggestions`.
- The batch was reviewed once. One finding showed a `Done when` point of `move-keeps-nested-and-qualified-names` unmet (the order of the imports); the user decided on 07-11-2026 to document it and amend the point. The other findings are in `move-keeps-the-visibility-of-an-exported-package` and `move-and-references-review-suggestions`; the bug met on the way is `move-on-a-root-with-two-eclipse-projects`. `bin-ci` of pull request 21: green. `bin/ci --all` of the worker: format, build, test (cli 885, core 723, daemon 974, protocol 397, shared 236, architecture 19), release-install, native, jdtls all passed. No coupling ceiling raised.

Tokens (one batch of three issues, shared by the three resolutions):
- worker: 329256
- reviewer: 89686
- supervisor: 33 calls, 26779 output, 177000 input + cache write (5401473 cache reads), over the period from the launch of
  the worker to the merge, the other work of the period included; exchanges with the user: 4 messages
