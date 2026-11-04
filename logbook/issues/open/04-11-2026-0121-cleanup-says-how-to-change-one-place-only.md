# gabier cleanup says how to change one place only

Opened: 04-11-2026 01:21

`gabier help cleanup` says "Each clean-up rewrites the whole file", and `docs/commands/cleanup.md` says jdtls answers one
edit that covers the whole text, but neither says what to do to change one place only. The way exists: `gabier actions`
on a range, then `gabier apply-action` with a title; the doc of `apply-action` even names "a concatenation to a text
block" as a rewrite of a range. Only that doc leads to it. Seen on eclipse.jdt.ls: to turn a few string concatenations of
a test file into text blocks, `cleanup stringConcatToTextBlock` would have rewritten every concatenation of the file, and
the user had to point to `apply-action`.

Done when:
- [ ] `gabier help cleanup` and `docs/commands/cleanup.md` say in one sentence that a clean-up applies to the whole file,
      and that for one place, `gabier actions` then `gabier apply-action` is the way, with the text block as the example.
- [ ] `docs/commands/actions.md` and `docs/commands/apply-action.md` link back to `cleanup` for the whole file, so that
      each of the three commands names the other two. The tests that check the help and the docs are updated. `bin/ci`
      passes.
