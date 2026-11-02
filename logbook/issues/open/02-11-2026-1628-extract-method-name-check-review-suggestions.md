# extract-method name check: review suggestions

Opened: 02-11-2026 16:28
On hold: findings of the review of 1934621, none breaks a `Done when` point; the user lifts the hold. The first one is a real defect on anonymous classes (a spurious `invalid-name`).

Done when:
- [ ] `JdtlsLanguageServer.java` (`isNamed`, `ownerOf`): anonymous classes are recognised on the real jdtls. Its label for an anonymous type starts with `new` (`new {0}() {...}` or `new Anonymous`, `JavaElementLabelsMessages.properties` of `org.eclipse.jdt.core.manipulation`, jdtls 1.63.0), which `STARTS_AS_A_NAME` takes for a name; two anonymous classes in one method, the first with `doubled(int)`, extraction into the second named `doubled`: accepted, shown by a scenario replayed on the real jdtls.
- [ ] `ownerOf` compared with `Optional.equals`: a member whose type is not found no longer equals any other such member; it fails with a `JdtlsException`, as a missing method does.
- [ ] The tests use the real labels and containers (`new Anonymous`-style labels for anonymous types, `Totals.java` as the container of a top-level type), not the invented `{...}` and empty container.
- [ ] A missing definition, an empty one and links are told apart in the message ("did not say where the method ... is declared" is the same for the first two today).
