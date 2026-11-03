# gabier rename answers for the name of a module

Opened: 03-11-2026 14:53

Met on 03-11-2026: `gabier rename --dry-run modules/cli/src/main/java/module-info.java 1 8 eu.gabier.client` (the name in
`module eu.gabier.cli {`) answers `internal-server-error`, with no description. Renaming a module is a text edit
today (the package rename ticket lists it among them); an agent should get a rename or a refusal that says so.

Done when:
- the rename of a module name renames it, and the `requires` of the other `module-info.java` files, or is refused with a
  key that says that the language server does not do it (then `gabier help rename` says it and the text edit stays);
  never `internal-server-error`; observed on the real jdtls first;
- `bin/ci` passes.
