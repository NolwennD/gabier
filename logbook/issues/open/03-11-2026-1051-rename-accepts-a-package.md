# gabier rename renames a package

Opened: 03-11-2026 10:51

An agent renames a Java package, with its folders, its `package` lines, its imports and its references, in one `gabier
rename` instead of one `gabier move` per source set and package (23 calls for `fr.craft` to `eu.gabier`). Met on
03-11-2026: `gabier rename --dry-run` on the `package` line of a file answers `language-server-no-such-symbol`.

Done when:
- `gabier rename <file> <line>:<column> <new-name>` on the name in a `package` line renames that package (and its
  sub-packages when jdtls does) in every file of the project the daemon serves: `--dry-run` writes nothing, the folders
  match the new name, `gabier diagnostics` is clean on a fixture of two source sets;
- a scenario of the catalogue (`add-scenario`) observed on the real jdtls first, replayed against it, and scripted in the
  fake; a package that cannot be renamed (invalid name, a name already taken) is a refusal with a key, listed in
  `gabier help rename`;
- `gabier help rename`, `docs/` and `.claude/rules/dogfooding.md` say it renames a package, and what stays a text edit
  (`module-info.java`, string literals, build files);
- `bin/ci` passes.

Depends on: rename-takes-its-position-as-line-colon-column.
