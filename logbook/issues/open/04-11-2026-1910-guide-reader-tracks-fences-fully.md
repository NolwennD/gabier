# The guide reader tracks every kind of code fence

Opened: 04-11-2026 19:10
On hold: no real use yet: `docs/improve.md` has an even number of plain fences today, nothing is dropped.

`GuideSections` sees a fence only as a line that starts with three backticks at column 0. Not tracked: `~~~` fences,
indented fences (in a list item), a fence of four backticks that holds a three-backtick line (the state flips wrongly).
An unclosed fence leaves the state "in a fence" to the end of the page, so every later `###` section is dropped from
`help --for` with no error.

Done when:
- [ ] a `~~~` fence, an indented fence and a longer fence that holds a shorter one are tracked as CommonMark says
      (tests in `GuideSectionsTest`);
- [ ] a test of `GuideTest` fails when `docs/improve.md` has an unclosed fence;
- [ ] `bin/ci` passes.
