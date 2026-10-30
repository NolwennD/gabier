# The long texts of the tests move to the resources

Opened: 30-10-2026 22:30

`.claude/rules/domain.md` ("Modern Java") says a text of more than about fifteen lines in a test is a file of the
`src/test/resources` of its module, read by a helper of the test fixtures. Today no module has test resources, and
`UnifiedDiffTest` holds 107 lines of text blocks (diffs and files); `ChangeApplierTest` and `RenameTest` hold 14 and
10. Reading these classes costs their whole text in context.

Done when:
- the texts of `UnifiedDiffTest` over about fifteen lines are files under `modules/core/src/test/resources`, read by a
  helper of the core test fixtures (how a test of a module on the module path reads a resource is shown by it and said
  in the Javadoc of the helper); the test class says in its names and comments what each text proves;
- the bytes of each text are the same as before (a test compares a file read from the resources with the former literal
  once, then the literal is deleted), and no assertion is weakened;
- the test classes that keep a text of ten to fifteen lines stay as they are; none over fifteen lines is left, in any
  module (a search is in the resolution);
- `bin/ci` green, coverage 100%.
