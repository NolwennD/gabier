# The guide answers "rename a class"

Opened: 13-11-2026 00:17

A friction met while using gabier (reported by the worker of the ticket 12-11-2026-2147): `gabier help --for="rename a class
ProblemFeed to ProjectFeed"` answered "nothing matches", although `gabier rename` renames a class and its test class. An agent
that is told to ask the guide before editing by hand is sent away from the right command. Per `.claude/rules/dogfooding.md`, a
wrong answer or an abstention on a real task is a ticket, and the fix is usually an entry of the guide or the words of its
`Words:` line (skill `extend-guide`).

Done when:
- `gabier help --for` answers the command `rename` for the task above, and for the same task worded differently by a person
  (rename a type, a class, a method, a field), each wording pinned as a question of `GuideQuestionsTest`; the entry that already
  covers `rename` is corrected, not duplicated.
- A task that is not a rename (move a class to a package, rename a package) still answers its own command: the questions that
  covered them pass unchanged.
