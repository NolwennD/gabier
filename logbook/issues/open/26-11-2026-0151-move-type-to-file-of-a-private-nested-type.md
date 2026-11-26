# Moving a private nested type that its outer type uses writes `private` on the new top-level type

Opened: 26-11-2026 01:51

Met while using gabier on another project (reported by the user; the move was then done by hand). `gabier move-type-to-file`
on a nested **record** declared `private` answered a dry run whose new file declares `private record ...` at top level,
which Java refuses (a top-level type is public or package-private). No refusal, no warning: an agent that applies the answer
writes a file that does not compile.

Reproduced on 26-11-2026 with the build of `main` (`0.0.0-dev`, after 0.7.1), on a folder that is an Eclipse project
(`.project`, `.classpath`, one source folder), a dry run of `gabier move-type-to-file --dry-run <file> 4:18` on:

    class J {
      private final Point p = new Point(1, 2);

      private record Point(int x, int y) {}
    }

The new file holds `private record Point(int x, int y) {}`. The same shape in a package (a `public` outer class, a field
of the nested type and a method that uses it) shows it too. **The trigger is the outer type using the nested one**: the
same record that the outer type does not use (alone in `class Shapes { private record Point(int x, int y) {} }`, with a
Javadoc or an annotation, in a package or not), and a private enum, interface and static class that the outer type does
not use, all come out with no `private`. The first report gave the unused case, which does not show it; it was tried
on 0.5.0 to 0.7.0 as well (same, no `private`), the used case only on the current build.

An agent that moves a private nested type gets a file that compiles, or a refusal that says what to do, never a silent
invalid file. Whether the other kinds of nested type show it when the outer type uses them is not known: check an enum, an
interface and a class too.

Done when:
- The case is reproduced on the real jdtls (observe first, as `.claude/rules/workflow.md` says: a throwaway replay that
  prints the change set), for a private nested record, and for the other kinds of nested type, and the resolution says
  which of them show it.
- A contract scenario of the catalogue holds the case, replayed against the real jdtls (skill `add-scenario`); its
  effect (the files once applied, compiled by the replay) is what is compared.
- The resolution says whose behaviour it is (jdtls's move refactoring, or the way gabier asks for it) and what was done:
  reported upstream, or a setting that gives the good result; a wrong answer of jdtls is relayed and documented, not
  guessed in advance (`.claude/rules/workflow.md`, "jdtls is never fought"). Only a light workaround in the adapter is
  allowed, and the resolution says why.
- `gabier help move-type-to-file` says what the agent sees and runs next for this case (for instance
  `gabier diagnostics` on the new file), in the form of the help rules (`.claude/rules/help.md`).
