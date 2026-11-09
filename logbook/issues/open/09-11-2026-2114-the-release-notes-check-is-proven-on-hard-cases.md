# The release notes check is proven on hard cases and on the way the workflow calls it

Opened: 09-11-2026 21:14
On hold: finding of the review of the idle stop and release notes pull request, no real use broken yet; lifted by the user or when a release meets it.

A regression of `scripts/release-notes.sh` or of its call in `.github/workflows/release.yml` would go unseen by `bin/ci`.

Done when:
- the stage `bin/ci.d/36-release-notes` has a case whose version file holds `&`, `\`, `@` and `$1`, and one with CRLF line breaks, and shows the notes keep the text as written;
- a check fails when `scripts/release-notes.sh` is not executable, or the workflow calls it the way the stage does (`sh`), so that the two cannot differ;
- `bin/ci` is green.
