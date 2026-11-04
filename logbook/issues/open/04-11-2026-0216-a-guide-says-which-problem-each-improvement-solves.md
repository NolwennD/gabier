# A guide says which problem each improvement solves, shipped with gabier

Opened: 04-11-2026 02:16
Depends on: 04-11-2026-0215-actions-names-the-gabier-command-that-applies-an-action

An agent with a piece of Java finds, in the installed gabier, what can be done to it and why: the language moves (text
blocks, switch expressions, pattern matching: the clean-ups of jdtls) and the structure (an `if` to invert, an `if
return else return` to simplify, which jdtls does not offer). Today the titles of `gabier actions` say what jdtls does,
not which problem it solves, and `gabier help` lists commands, not situations. gabier is used without a clone (README),
so the guide cannot live in `docs/` alone.

Done when:
- [ ] `docs/improve.md` is the one source, read on GitHub and linked from the README; the build copies it into the
  resources of the cli, and the native cli of the release prints it from a folder with no clone (`gabier help guide`,
  the name is chosen in the ticket; `gabier help` points to it).
- [ ] one section per situation: its words, the command or the action, a before/after, its limits. For the language:
  every clean-up of `gabier help cleanup` with the problem it solves. For the structure: what jdtls offers and what it
  does not (invert an `if`, if/return, join and split of conditions: by hand), taken from the source of jdtls. First
  entries from situations met here: a concatenation that is not only literals (`HelpText.java` 55-60: no text block from
  jdtls), one place and not the whole file (`cleanup`), a record component, the package of one source set.
- [ ] the way to extend it is a skill, applied once to add an entry: a situation met with no entry becomes an entry.
  `bin/ci` passes.
