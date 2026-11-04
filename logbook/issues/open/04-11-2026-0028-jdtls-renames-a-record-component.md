# jdtls renames a component of a record

Opened: 04-11-2026 00:28
On hold: waits for the user, who decides whether and when to open the issue and the pull request on eclipse.jdt.ls

An agent renames a component of a record, with its accessor and its uses, with one command, because jdtls does it. Read
in the source of jdtls v1.63.0: `RenameHandler.java` gives the element at the position to `RenameSupport.create`
(`corext/refactoring/rename/RenameSupport.java`), a fork of jdtls that has a case for a field, a local variable and a
method but none for a record: the parameter of a compact constructor is taken for a local variable (the
NullPointerException of the rename), the accessor is declined by `PrepareRenameHandler`, and a rename from the component
misses the uses by simple name in the body. JDT UI redirects those elements to the component (Eclipse bugs
[559681](https://bugs.eclipse.org/bugs/show_bug.cgi?id=559681) and
[566507](https://bugs.eclipse.org/bugs/show_bug.cgi?id=566507), 2020; JDT UI commits:
[559681](https://eclipse.googlesource.com/jdt/eclipse.jdt.ui/+/refs/tags/Y20200310-0525%5E%21/),
[566507](https://eclipse.googlesource.com/jdt/eclipse.jdt.ui/+/e2d1c1dcc7573198641437a36ff76b413f603425%5E)); the fork
never took it. With an explicit accessor, the rename also turns `label()` into `getCaption()`: `RenameHandler` passes
`UPDATE_GETTER_METHOD`, and the accessor of a component is taken for a getter (seen 04-11-2026). To confirm with a stack
trace of the NullPointerException before it is said upstream.

Done when:
- the text of the upstream issue is written in English (the record with and without a compact constructor, what jdtls
  answers, the cases of the fork, the bugs of JDT UI) and given to the user, who opens it, with a pull request of the
  fix if the user wants it (a test in `RenameHandlerTest`);
- once a jdtls milestone with the fix is out: `jdtls_version` and `jdtls_sha256` of `mise.toml` are bumped together, the
  scenario of the rename of a component is written (an effect that compiles), and the limit is removed from `gabier help
  rename`, `docs/commands/rename.md` and the code that maps the failure to a key.
