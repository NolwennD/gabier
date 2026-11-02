# gabier references refuses a symbol that does not resolve

Opened: 02-11-2026 19:34

Met while diagnosing `move-rewrites-references-in-other-modules`: `gabier references <file> 6:35` on `ErrorKey` in
`implements ErrorKey`, in a file of `modules/protocol` whose project did not see `shared`, answered with exit 0 the
references of the enclosing type `ProtocolErrorKey`. The agent takes them for those of `ErrorKey`. A position on a name
that does not resolve is refused with a key that says so, never answered for another symbol.

Done when:
- `references` at the position of a type name that does not resolve is refused (key in `gabier help references`), and a
  position between symbols still answers as today; a scenario and its replay show it;
- `bin/ci` passes.
