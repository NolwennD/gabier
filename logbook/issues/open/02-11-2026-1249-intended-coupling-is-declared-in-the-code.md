# The coupling to an engine that gabier chooses is declared in the code

Opened: 02-11-2026 12:49
On hold: for memory, decision of the user (02-11-2026): "in time"; the user lifts the hold when the coupling work
(`...-scenarios-keep-the-coupling-to-jdtls-in-the-adapter.md`) is done, or when a second Java language server is tried.

A coupling to jdtls that gabier wants (the 19 ids of `CleanUp` in the domain, the shape of an answer that a scenario pins) is indistinguishable in the code
from one that slipped in. The user wants the intent said in the code, and counted: what is coupled on purpose to which engine, and why.

What was seen in `seed4j` (read 02-11-2026, nothing run): (1) its markers are plain annotations on packages, `@BusinessContext(name, description)` and
`@SharedKernel`, with a retention at run time, which its `HexagonalArchTest` reads (`packagesWithAnnotation`) to build the architecture rules; gabier
has only `@ExcludeFromGeneratedCodeCoverage` and takes its contexts by the names of the packages. (2) jQAssistant is not used on `seed4j` itself: a
generator module installs it in a generated project (the `jqassistant-maven-plugin` and `jqassistant-context-mapper-plugin`, a `.jqassistant.yml`, with
asciidoctor for the reports). The context-mapper plugin is made for the vocabulary of a DDD context map, in which "adopting the model of another
context" (conformist) and "translating it" (anti-corruption layer) are two named relations: the `CleanUp` enum is a conformist coupling to Eclipse, the
adapter of the `daemon` an anti-corruption layer. Not read: the documentation of that plugin, and whether a Gradle build runs jQAssistant (gabier builds
with Gradle, `seed4j` with Maven).

Done when:
- [ ] A short comparison in the resolution of two ways to declare it: an own annotation (for instance an engine and a reason on a type or a package) read by
      ArchUnit, which is already in the build (`:architecture`), against jQAssistant (what it needs in a Gradle build, what it reports that ArchUnit cannot, its
      weight); with a recommendation.
- [ ] The way retained is applied to `CleanUp` and to one more case, with a test that fails when the declaration is missing where an engine's type or id
      appears in the core, and a count of the declared couplings shown by `bin/ci`. `bin/ci` passes.
