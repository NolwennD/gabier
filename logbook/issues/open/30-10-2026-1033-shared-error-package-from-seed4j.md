# The shared error package of seed4j is in gabier

Opened: 30-10-2026 10:33

Copy the package `../seed4j/src/main/java/com/seed4j/shared/error/domain/` as it is, with its tests, into the `shared`
module as `fr.craft.gabier.shared.error.domain` (`Assert`, `AssertionException`, the `...Exception` classes,
`ErrorKey`, `StandardErrorKey`, `ErrorStatus`...). The user's decision: the code is meant to be copied, seed4j generates
it into every application it creates. Only what the Spring and web side of seed4j adds (the `infrastructure` packages)
is left out. The domain of gabier then validates its values with `Assert` in the compact constructors of its records.
Adapt only what cannot compile here (packages, test annotations of seed4j that gabier does not have, AssertJ in the
tests); keep the rest unchanged so that a later diff with seed4j stays readable, and say in the resolution what changed.

Done when:
- the package and its tests are in `shared`, with the coverage at 100% on the classes kept, and any exclusion
  annotated with its reason (or the class removed, if nothing in gabier can use it, saying why);
- the module `shared` exports the package, the architecture tests accept it, and the native image still builds;
- one existing place of gabier that validates a value by hand, if there is one, uses `Assert`; otherwise none is
  invented.

Depends on: nullaway-and-jspecify-on-every-module (the code uses `@Nullable` and `@Contract`).
