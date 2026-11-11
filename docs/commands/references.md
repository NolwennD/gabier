# gabier references

`gabier references [--include-declaration] <file> <line>:<column>` lists where the symbol at a position is used, instead of
searching the text: a type, a method, a field, a local variable or a parameter, at its declaration or at a use. One line per
place, `file:line:column: source`, the file as the daemon gives it (absolute), the line and the column **from 1**, as an
editor and a compiler show them, and the source line of the place without its indentation. The places are ordered by file,
then by position. The uses in the test sources of the project are listed, and so are those in another package. Nothing is
written. The exit code is 0 whenever the daemon answered.

```text
$ gabier references src/main/java/demo/Ledger.java 11:17
/work/demo/src/main/java/demo/app/Bank.java:8:16: ledger.deposit(10);
/work/demo/src/test/java/demo/LedgerTest.java:6:16: ledger.deposit(20);
```

**The declaration.** It is not listed unless `--include-declaration` is given (the option may stand anywhere after the
command). It is then listed apart, under `declaration:`, and the uses under `references:`:

```text
$ gabier references --include-declaration src/main/java/demo/Ledger.java 11:17
declaration:
/work/demo/src/main/java/demo/Ledger.java:11:17: public void deposit(int amount) {
references:
/work/demo/src/main/java/demo/app/Bank.java:8:16: ledger.deposit(10);
```

**A symbol nothing uses** prints `no references` and exits with 0 (after the declaration, when it was asked for).

**`--json`** gives the result as the daemon gave it, the positions **from 0** as the protocol counts them and the source
lines exact (with their indentation):

```text
$ gabier --json references --include-declaration src/main/java/demo/Ledger.java 11:17
{"declarations":[{"file":"/work/demo/src/main/java/demo/Ledger.java","line":10,"column":16,"source":"    public void deposit(int amount) {"}],"references":[{"file":"/work/demo/src/main/java/demo/app/Bank.java","line":7,"column":15,"source":"        ledger.deposit(10);"}]}
```

A symbol nothing uses gives two empty lists, `{"declarations":[],"references":[]}`.

In text, every line is neutralised for the terminal, the source line included, as a source line is hostile text: see
[Text in a terminal](../cli.md#text-in-a-terminal). The cli sends the absolute path of the file to the daemon, which alone
cleans it.

## What is and is not listed

- **The accessors of a field are not references of the field.** By default jdtls lists the call of the getter and of the
  setter of a field with the uses of the field; gabier starts it with `java.references.includeAccessors` set to false. To
  find the callers of a getter, ask for the getter.
- **Only the symbols of the project have references.** The command asks jdtls whether there is a symbol to rename at the
  position (`textDocument/prepareRename`) before it asks for the references, and jdtls declines for a type of the JDK or of
  a library, as for a blank, a comment, a keyword, a literal, a package (`demo` in `import demo.Ledger;`) or a generated
  method: all are refused as `language-server-no-such-symbol`. This is a limit: the uses of `String` in the project are not
  listed.
- **No place in a library is listed.** gabier does not declare `classFileContentsSupport`, so jdtls never answers a location
  in a class file or a jar, and `java.references.includeDecompiledSources` is set to false. Such a place is not listed
  and not refused.
- **The test sources are searched** (`java.search.scope` is left at its default, `all`). A source folder is a test source
  when the project marks it so (`test=true` in the `.classpath` of an Eclipse project; the test source set of a Gradle
  or Maven project).
- **A name that does not resolve is refused**, as a position on nothing is: the type of an `implements`, a field or a
  parameter whose import names a package the project does not see (a module that is not required, a package that is not
  exported, a package that does not exist) has no symbol to find the uses of. jdtls declines it as it declines
  a blank, so the key is the same, `language-server-no-such-symbol`; fix what `gabier diagnostics` reports on the file
  (`cannot be resolved to a type`, `is not accessible`) and ask again. A fully qualified name that does not resolve
  (`foo.bar.Third` in `foo.bar.Third third;`) is not refused by jdtls: it is answered as a symbol nothing uses, `no
  references`.
- **A position on nothing** (a blank, a comment, a keyword) is refused. Without the check, jdtls would answer the references
  of the member that encloses the position, which is a wrong answer.

## How it is done

The daemon method is `references` (parameters `file`, `line` and `column` from 0, and `includeDeclaration`, `true` or
`false`). The adapter asks `textDocument/prepareRename` at the position, then `textDocument/references` with
`includeDeclaration` false. jdtls does not say which of the places it answers is the declaration, so with
`includeDeclaration` the adapter asks a second time with it true and takes the places that are new as the declaration. The
locations are put in the order of the files then of the positions, and the line of source of each is read from the file
on disk by the daemon (a file that changed since jdtls answered is refused as `editing-stale-edit`). `ScenarioReplayTest`
replays the contract scenarios `references-*` against the real jdtls.

## Refusals

A refusal is the problem object on the standard error, exit code 2, and nothing is written. These are the keys the
command can answer, and the help of the command (`gabier help references`) lists them:

- `language-server-no-such-symbol`: no symbol of the project at the position: a blank, a comment, a keyword, a package,
  a type of the JDK or of a library, or a name that does not resolve.
- `editing-file-missing`: a file that holds a reference is gone since the language server answered.
- `editing-stale-edit`: a file that holds a reference changed since the language server answered.
- `language-server-file-outside-project`: the file is not in the project the daemon serves.
- `language-server-file-missing`: the path is under the project and no file is there.
- `language-server-not-a-file`: the path is a folder.
- `language-server-request-failed`: the language server failed on the request with an error of its own, which the
  problem relays (its code and its message; the cause is in the log of the daemon, see [the daemon](../daemon.md)).
- `language-server-not-ready`: the Java language server is not ready.
- `usage-error`: the arguments are not those of the command.
- `daemon-start-failed`: no daemon answered and `gabier-daemon` could not be started.
- `daemon-connection-failed`: the connection to the daemon was lost, or it hung up without answering.
