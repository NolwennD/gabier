# gabier rename finds a package name split by a comment or a line break

Opened: 03-11-2026 11:59
On hold: edge case found by the review of `rename-accepts-a-package`; no real use met yet. The user lifts it if it bites.

`gabier rename` reads the package name with a regex that needs `package` and the name on one line, with no comment between
(`PackageDeclarations`). `package /* x */ demo.app;` and `package demo.\n app;` answer `language-server-no-such-symbol`
although jdtls would rename the package.

Done when:
- both inputs rename the package, or are refused with a message that says why; a test per input;
- `bin/ci` passes.
