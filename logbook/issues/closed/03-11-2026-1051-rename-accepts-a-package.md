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
  `gabier diagnostics` reports no error on a fixture with a main and a test source set after the two calls (the import
  that jdtls adds to the test class stays a redundant-import warning, which `gabier organize-imports` removes: the docs say
  so);
- a scenario of the catalogue (`add-scenario`) observed on the real jdtls first, replayed against it, and scripted in the
  fake; a package that cannot be renamed (invalid name, a name already taken) is a refusal with a key, listed in
  `gabier help rename`;
- `gabier help rename`, `docs/` and `.claude/rules/dogfooding.md` say it renames a package, that the same package of
  another source set is a call of its own, and what stays a text edit (`module-info.java`, string literals, build files);
- `bin/ci` passes.

Depends on: rename-takes-its-position-as-line-colon-column.

## Resolution

Done in `ee951b8` (batch with `rename-takes-its-position-as-line-colon-column`). `gabier rename` on the name in a `package`
line renames the package and moves its files to the folder of the new name. Observed on the real jdtls first: it declines
`prepareRename` on a package name ("Renaming this element is not supported") but `textDocument/rename` works, so the adapter
skips the gate for a package name (`PackageDeclarations`); it renames the prefix up to the clicked segment, takes the whole
new name, leaves the sub-packages, and works in the source folder of the clicked file only; it moves a file with a `.temp`
create, a rename and a delete, which `WorkspaceEdits` turns into the domain change set. No new `java.*` setting or client
capability: the existing resource operations are enough. The docs read were not those of Eclipse JDT LS but the answers of
the server itself.

Scope reduced to one source folder (decision of the supervisor, 03-11-2026, `8212aef`): the same package of the tests is a
second call (about 2 calls per package instead of one `gabier move` per source set and package). The follow-up
`rename-a-package-in-every-source-set` is on hold. `Done when` "diagnostics is clean" became "reports no error" (`cbce051`):
jdtls adds `import demo.shop.Greeter;` to the test class, a redundant-import warning once the second call has put the class
in the same package, which `gabier organize-imports` removes; the docs say so. Proven by the scenario and its sequel
(`PackageRenameScenarios`, `ScenariosTest`, `SequelsTest`, replayed on the real jdtls by `ScenarioReplayTest`).

The ceiling `MOST_COMPILER_MESSAGES` of `JdtlsCouplingTest` goes from 4 to 5: the diagnostics scenario of the two calls holds
the compiler message of that warning, not compared.

Checked: the worker's full `bin/ci` on the final commit, format 15s, build 5s, test 237s, native 197s, jdtls 550s, all
passed; the check `bin-ci` of the pull request passed (12m13s). Review (once, neutral): BLOCKING, no test of diagnostics
after the second call (fixed, with the amended point); suggestions: the package line with a comment or a line break is not
found (`rename-package-line-with-comment-or-line-break`, on hold), a paragraph of the docs unwrapped (fixed). Not proven by
a worker report (it never reached the supervisor).

Tokens: see `rename-takes-its-position-as-line-colon-column` (one batch, one worker, one reviewer, one supervisor period).
