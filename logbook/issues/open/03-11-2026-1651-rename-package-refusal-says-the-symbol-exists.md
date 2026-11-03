# gabier rename of a package refuses with a message that is wrong

Opened: 03-11-2026 16:51

An agent that gives a package rename a new name jdtls cannot apply is told that there is no symbol, and goes looking for
a position error that is not there. Met on 03-11-2026 with the installed gabier, on the line
`package eu.gabier.shared.generation;` of `ExcludeFromGeneratedCodeCoverage.java`:
`gabier rename --dry-run <file> 1:20 shared.coverage` (the position is on `shared`, the new name does not start with the
current `eu.gabier`) answers `language-server-no-such-symbol: There is no symbol at 0:19`, while the position is on the
name of a package; `1:30 eu.gabier.shared.generated` (on the last segment, whole new name) renames it.

Done when:
- the case above is refused with a key and a message that say what is wrong (the new name must be the whole name, the
  clicked segment decides the prefix renamed) and not that there is no symbol; the key is listed in `gabier help rename`;
- observed on the real jdtls first (what it answers: an empty edit, a refusal?), a scenario in the catalogue replays it;
- `bin/ci` passes.
