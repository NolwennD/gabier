# Every release says what changed in the commands

Opened: 09-11-2026 19:52

Real gap met after the release 0.5.0: the notes of 0.2.0, 0.3.0 and 0.4.0 hold only the list of attached files
(`.github/release-notes.md` is one template for every version), so a user of 0.2.0 cannot tell whether a command changed
before 0.5.0. The "New in 0.5.0" section was added by hand to the published release.

Done when:
- the notes of a release carry a "New in <version>" section read from a file of the repository, one per version
  (the worker chooses the place, for instance `docs/releases/<version>.md`), that `scripts/package-release.sh` puts into
  `release-notes.md` before the attached files; the template stays generic;
- a tag whose version has no such file, or one that is blank, fails the release workflow before any asset is published,
  with a message that names the file (a test or a check of `bin/ci` that does not need a tag, for a stage that can be run
  on a branch);
- the file of 0.5.0 exists and holds the text of the published release (decision of the user, 09-11-2026: the published releases are not touched and the older versions are not back-filled, he is the only user); the files of later versions are written before their tag
- `AGENTS.md` ("Releasing") and the skill or rule that describes a release say that the file is written before the tag;
- `bin/ci` is green.

Not part of this ticket: editing any published release.
