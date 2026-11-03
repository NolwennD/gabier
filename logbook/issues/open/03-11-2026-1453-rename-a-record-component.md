# gabier rename renames a record component

Opened: 03-11-2026 14:53

An agent renames a component of a record, and its accessor and uses, with one command. Met on 03-11-2026 with the installed
gabier: `gabier rename --dry-run modules/core/src/main/java/eu/gabier/editing/domain/Location.java 14 29 path` (the
component `file` of `record Location(Path file, Position position)`) and the same on `severity` in `Diagnostic.java`
12:35 answer `internal-server-error`, with no description. A component of an enum constant, a type parameter, a
constructor name, an annotation element and a class name rename without a problem.

Done when:
- the rename of a record component renames the component, its accessor calls and its uses in the other files, or is
  refused with a key that says why; never `internal-server-error`; a scenario observed on the real jdtls first;
- `gabier help rename` lists the refusal if there is one; `bin/ci` passes.
