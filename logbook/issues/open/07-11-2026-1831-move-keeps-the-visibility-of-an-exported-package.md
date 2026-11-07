# gabier move does not narrow the export of a package when its classes join a package that is already exported

Opened: 07-11-2026 18:31

Found in the review of `move-updates-the-exports-of-the-module-info`: when the last classes of an exported package move to
a package the module already exports, `ModuleExports` drops the old directive instead of renaming it, and does not look
at the `to` clauses (`modules/daemon/src/main/java/eu/gabier/daemon/ModuleExports.java:56`). So:
- old `exports a.b;` (to everyone), target already `exports t to x;`: the unqualified export is dropped, the moved classes
  are then visible to `x` only;
- old `exports a.b to y;`, target `exports t;`: `y` is lost with no word, the classes stay visible to everyone (wider,
  not narrower, than `y` only, but not what the module said).

An agent that moves classes keeps the module's own visibility rules, or is told they changed.

Done when:
- [ ] on the cases above (and `opens`), the module-info keeps the widest of the two visibilities, or the move is refused
      with a key that says the `exports`/`opens` of the two packages differ; one of the two, said in `gabier help move`
      and `docs/commands/move.md`;
- [ ] a unit test per case in `ModuleExportsTest` and a scenario with its replay on the real jdtls (the effect: the
      module-info after the move compiles); `bin/ci` passes.

Depends on: move-updates-the-exports-of-the-module-info (merged with the batch of 07-11-2026).
