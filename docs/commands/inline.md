# gabier inline

`gabier inline <file> <line>:<column>` replaces a local variable, a method or a constant by its value instead of editing every use by hand.
On a local variable, or on the declaration of a method or a constant, every use is replaced and the declaration is removed; on one
use of a method or a constant, that use only is replaced and the declaration stays. It is the counterpart of `extract-variable` and
`extract-method`. The position is on the **name** of what to inline, **from 1** as `diagnostics` shows it, and is sent to
the daemon from 0. `--dry-run` (anywhere after the command) writes nothing and shows what would change.

The answer lists the files that changed (`modified <file>`) and then the unified diff, preceded by `dry run, nothing
written` for a dry run; with `--json`, the `ChangedFiles` object of the other commands that change files.

```text
$ gabier inline --dry-run src/main/java/demo/Prices.java 4:22
dry run, nothing written
modified /work/demo/src/main/java/demo/Prices.java
modified /work/demo/src/main/java/demo/Checkout.java

--- /work/demo/src/main/java/demo/Prices.java
+++ /work/demo/src/main/java/demo/Prices.java
@@ ... @@
-    static final int VAT = 20;
-
...
--- /work/demo/src/main/java/demo/Checkout.java
+++ /work/demo/src/main/java/demo/Checkout.java
@@ ... @@
-        return net + Prices.VAT + prices.handling(net);
+        return net + 20 + prices.handling(net);
```

## What is inlined where

- **A local variable**: every use is replaced by the expression that initializes it, and the declaration is removed,
  whatever the position (on the declaration or on a use). jdtls adds parentheses where the value could otherwise read
  differently, as in a concatenation (`"tax: " + (net * VAT / 100)`), and leaves them out where precedence already holds
  (`net + net * VAT / 100`).
- **A method**: a position on its **declaration** replaces every call, in every file, by the body of the method and
  removes the method. A position on **one call** inlines that call only, and the method stays for the others.
- **A constant** (a `static final` field): the same as a method, a position on the declaration inlines every use and
  removes the constant, a position on one use inlines that use only.

## How it is done

The command asks jdtls for the code action `refactor.inline` at the position (an empty range) and resolves it for its
edit, as `organize-imports` does (`RefactorProcessor.getInlineProposal` in the jdtls source). The client declares the
kind `refactor.inline` among its code action literals and `resolveSupport` for `edit` (`JdtlsCapabilities`); no setting of
`JdtlsSettings` is involved. `only` chooses which processors jdtls runs, not which actions it returns: the refactor
processor answers all of its `refactor.*` actions ("Change type to 'var'" came back for a variable), and "Make static"
has the kind of the inlinings. The adapter keeps the action of the kind `refactor.inline` that is titled "Inline ..." ("Inline local variable", "Inline Method", "Inline
Constant").

**Who refuses.** jdtls offers no inlining, or an inlining with no edit, and says nothing of why, so gabier has one key
for all of it: a position that is not on a local variable, a method or a constant (a parameter, a keyword, the `package`
line); a variable assigned more than once; a method that is recursive or abstract; a variable or a constant that nothing
uses. Nothing is written.

The daemon method is `inline` (parameters `file`, `line`, `column`, from 0, and `dryRun`).

## Refusals

A refusal is the problem object on the standard error, exit code 2, and nothing is written. These are the keys the
command can answer, and the help of the command (`gabier help inline`) lists them:

- `language-server-not-inlinable`: nothing the server can inline at the position (not on a variable, a method or a
  constant; a variable assigned more than once; a recursive or abstract method; a declaration that nothing uses).
- `language-server-file-outside-project`: the file is not in the project the daemon serves.
- `language-server-file-missing`: the path is under the project and no file is there.
- `language-server-not-a-file`: the path is a folder.
- `editing-stale-edit`: the change cannot be applied to the files as they are.
- `language-server-request-failed`: the language server failed on the request with an error of its own, which the
  problem relays (its code and its message; the cause is in the log of the daemon, see [the daemon](../daemon.md)).
- `language-server-not-ready`: the Java language server is not ready.
- `usage-error`: the arguments are not those of the command.
- `daemon-start-failed`: no daemon answered and `gabier-daemon` could not be started.
- `daemon-connection-failed`: the connection to the daemon was lost, or it hung up without answering.
