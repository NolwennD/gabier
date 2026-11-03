# gabier rename renames a package

Opened: 03-11-2026 10:51

An agent renames a Java package, with its folders, its `package` lines, its imports and its references, in one `gabier
rename` instead of one `gabier move` per source set and package (23 calls for `fr.craft` to `eu.gabier`). Met on
03-11-2026: `gabier rename --dry-run` on the `package` line of a file answers `language-server-no-such-symbol`.

Scope (decision of the supervisor, 03-11-2026, after jdtls was observed): jdtls renames a package in the source folder of
the clicked file only, and not its sub-packages. This ticket takes that behaviour as it is; the same package in another
source set (tests) is a second call, and the follow-up `rename-a-package-in-every-source-set` is on hold.

Done when:
- `gabier rename <file> <line>:<column> <new-name>` on the name in a `package` line (the full new name is given; the
  clicked segment decides the prefix renamed, as jdtls does) renames that package in the source folder of the file:
  `--dry-run` writes nothing, the folder of the package matches the new name and no empty old folder is left,
  `gabier diagnostics` is clean on a fixture with a main and a test source set after the two calls;
- a scenario of the catalogue (`add-scenario`) observed on the real jdtls first, replayed against it, and scripted in the
  fake; a package that cannot be renamed (invalid name, a name already taken) is a refusal with a key, listed in
  `gabier help rename`;
- `gabier help rename`, `docs/` and `.claude/rules/dogfooding.md` say it renames a package, that the same package of
  another source set is a call of its own, and what stays a text edit (`module-info.java`, string literals, build files);
- `bin/ci` passes.

Depends on: rename-takes-its-position-as-line-colon-column.
