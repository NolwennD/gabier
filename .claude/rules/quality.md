# Quality gates

Every ticket leaves these gates green. The supervisor checks them before closing a ticket (see the
`ticket` skill). The gates are put in place by the logbook issues on coverage, on architecture
tests and on formatting; until those are closed, the same properties are checked by hand.

- **Formatting is automatic**: Java files are formatted by
  [open-java-format](https://github.com/openjavaformat/open-java-format) (one style, 120 columns)
  from a pre-commit hook managed with [hk](https://github.com/jdx/hk). Never format by hand and
  never bypass the hook (`--no-verify`).

- **Coverage is 100%**: no missed line and no missed branch, per class (JaCoCo, as in the
  `chatbot` project). Never lower the threshold to make a ticket pass.
- **Exclusion from coverage is the exception**: only for code too complicated to test, or very
  unlikely to ever happen. Annotate it with `@ExcludeFromGeneratedCodeCoverage(reason = "...")`.
  The reason is mandatory and says why; an empty one is a failure. Code that is merely tedious to
  test is tested. The annotation's name must keep the word `Generated`: JaCoCo ignores what is
  annotated with such an annotation. Every use is challenged in the review.
- **The core stays almost pure Java**: the domain depends only on `java..` and a very short
  allow-list. Widening that list is the user's decision, not an agent's. Architecture tests
  (ArchUnit, inspired by `chatbot` and [seed4j](https://github.com/seed4j/seed4j)) enforce it,
  next to the module boundaries that JPMS already enforces at compile time.
- **Modern Java**: write idiomatic code for the release of the module (records, sealed types,
  pattern matching, sequenced collections, and what the release adds), not older idioms.
- **Review before closing**: a neutral Java reviewer reads the change before the ticket is closed,
  and its blocking findings are fixed first.
