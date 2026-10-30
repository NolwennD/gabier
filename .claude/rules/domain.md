---
paths:
  - 'modules/*/src/main/java/**'
  - 'modules/*/src/test/java/**'
  - 'architecture/src/**'
---

# Java: domain, modelling, naming and modern idioms

The dependencies between layers, the purity of the domain, null safety and the architecture tests are in
`.claude/rules/quality.md` and are not repeated here. This rule is about how a context is shaped and how its Java is
written; the last section applies to the tests too. What no test checks yet is marked _(not checked yet)_: the first
change that needs a check adds it as a `@Nested` class of `HexagonalArchTest`.

## Placement

- A new business concept is a new context: a sub-package of `fr.craft.gabier` with its `domain`, `application` and
  `infrastructure.primary` / `infrastructure.secondary` packages, each with its `package-info.java`
  (`@NullMarked`). Do not spend time on its name: it can be renamed later.
- Code shared between contexts goes to the `shared` module, minimal and specialised. Nothing else is shared.
- A framework class never appears in a `domain` package. A domain service is created with `new` by the use case of the
  `application` that calls it.

## Visibility

- **The lowest visibility that compiles is the right one.** `public` on a type is a decision: the package publishes it
  to the rest of the code (and, across a module, to the `exports` of its `module-info`), never the default the IDE
  writes.
- A top-level type that no other package names is **package-private**: a step of the plan, an `XxxErrorKey`, a helper
  of an adapter.
- Widen when the compiler asks, never in anticipation: a type becomes `public` in the same commit as its first use
  outside its package. The same for members: `private` field, `private` constructor, `private static` helper;
  `protected` only for a hierarchy that already exists.
- _(not checked yet)_ `HexagonalArchTest.Visibility`: a public top-level type that no class of another package depends
  on fails the build. Sub-packages are other packages; a dependency on a nested type counts for its enclosing type.
  Exceptions: the `Main` classes, launched by the JVM and named in the build.
- A test lives in the same package as what it tests, so it never justifies a `public`.

## Modelling (types first)

- One type per concept: `record Position(int line, int column)`, with its invariants checked by `Assert` in the compact
  constructor, normalisation included. Never a bare `String`, `Path` or `int` in a domain signature when it carries a
  meaning.
- `record` by default, `sealed interface` plus records for a choice (`Step`, `FileChange`). Immutable: an operation
  returns a new instance, a collection held by a record is made immutable in its constructor (`List.copyOf`).
- A business collection is a type of its own once it has methods (`ChangeSet`), not a `List<Xxx>` passed around.
- `Optional` models an absence in a **field or a return type, never in a parameter**: a method that would take one takes
  the plain value and the caller unwraps it (`map`, `ifPresent`, `orElseGet`). `null` is not an input either, and is
  never returned (NullAway holds the nulls). _(not checked yet)_ an `HexagonalArchTest.Modeling` holds the `Optional`
  half.
- A construction with three parameters or more, or two of the same type, goes through a step builder; below, a `record`.

## Naming

- Plural for what handles several elements. A use case of the `application` is named after what it does (`Diagnose`,
  `Rename`), not `XxxApplicationService`.
- A port is named after what it holds or asks for (`FileStore`, `JavaLanguageServer`).
- A secondary adapter is named **its technology followed by the exact name of the port it implements**
  (`NioFileStore implements FileStore`). `JdtlsLanguageServer` predates this rule and is not renamed here. A primary
  adapter is named after its protocol or its command.
- Code, messages and API in English.

## Errors

- A technical or developer error (a violated precondition) is an `Assert` of `shared.error.domain`, not unit tested.
- A business error is a dedicated exception of its context, extending `GeneratorException`, built with
  `GeneratorException.badRequest(XxxErrorKey.KEY).message(...).addParameter(...)` as `EditingException` does. Its key
  is in the context's `XxxErrorKey`; an adapter turns it into a problem object, never into a message of its own.
- An absence is an error thrown by the domain (`fileMissing`), not a `null` or an empty `Optional` that someone else
  has to read.

## Modern Java

- Write idiomatic code for the release of the module (records, sealed types, pattern matching, sequenced collections,
  and what the release adds), not older idioms.
- **Text blocks** (`"""`) for any string that spans several lines or escapes special characters: a JSON or JSON-RPC
  message, a source file given to jdtls, a diff, a usage text. A concatenation of literals with `\"` or `\n` in it is
  written as a text block, and `.formatted(...)` fills it in, never a `+` in the middle of it. A short string on one
  line stays a plain literal, even with an escaped quote.
- A text block ends with a line break unless its closing delimiter sits on the last line of text. Where the line matters
  (the protocol of the daemon is one JSON object per line), join the lines with `\<line break>`, and keep a trailing
  space with `\s`. A test that compares text compares what the block really holds, not what it looks like.
