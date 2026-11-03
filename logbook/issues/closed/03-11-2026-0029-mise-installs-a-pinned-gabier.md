# mise installs a pinned gabier

Opened: 03-11-2026 00:29
Depends on: 03-11-2026-0029-a-release-installs-with-one-script.md

A Java project that uses mise pins gabier in its own `mise.toml`, like its other tools, with
`mise use github:NolwennD/gabier@<version>`. Each clone of that project then gets the same gabier. Today the cli is the
only thing mise installs this way: the mise github backend takes one release file per tool, and the daemon is a second
file. jdtls is installed by neither. Per platform, the release should hold one archive with `bin/gabier` and
`bin/gabier-daemon` next to each other, where the cli already looks. It should also say how jdtls gets installed, for
example through a `postinstall` of the mise tool, or with jdtls inside the archive. The ticket chooses between them and
says why in its resolution.

Done when:
- In a project whose `mise.toml` holds only `"github:NolwennD/gabier" = "<version>"` and a JDK 25, `mise install` gives
  a `gabier` whose `gabier diagnostics` answers, with no variable set.
- Changing the version in that `mise.toml` and running `mise install` switches the cli, the daemon and the jdtls they
  use together.
- The proof is made with a mise whose data folder is temporary, on a pre-release tag that the supervisor pushes with the
  go of the user.
- The README says how to pin gabier with mise.

## Resolution

Fixed in `f3c4138` (pull request #8), with the follow-up `38c120f` (pull request #9).
- A release attaches `gabier-linux-x86_64.tar.gz`: `bin/gabier`, `bin/gabier-daemon`, `lib/` and `jdtls/`. The pinned
  jdtls is downloaded while packaging and checked against the sha256 of `mise.toml`.
- The daemon finds `jdtls/bin/jdtls` above its own `lib/` (`JdtlsLocation`), after `GABIER_JDTLS` and before
  `~/.local/share/jdtls/<version>`.
- jdtls goes inside the archive, not into a `postinstall`. A `postinstall` is an option of the tool in the `mise.toml`
  of the user, which each project would have to write and keep in step with the version. jdtls has no entry in the
  registry of mise. With the archive, the three come from one file, so a change of version changes them together.
- The archive name carries no version and differs from the bare cli, so the autodetection of the github backend picks it
  (+10 for an archive). AGENTS.md says not to rename the two.
- The README has a "With mise" section.

Checked by the worker (`bin/ci`):
- Format and build passed.
- Test passed: cli 598, core 693, daemon 866, protocol 383, shared 236, architecture 19.
- Native failed on the first run, then passed alone with `--stage native` after the fix.
- jdtls passed, alone (452s).
- `bin-ci` on GitHub passed, in 11m58s.

Proof on the pre-release `v0.1.0-rc.2`, pushed with the go of the user:
- The release run passed (version, `check / bin-ci`, release), and the release holds the five files.
- With a temporary mise data folder, in a Gradle project whose `mise.toml` holds only `java = "temurin-25"` and
  `"github:NolwennD/gabier" = "0.1.0-rc.2"`, `mise install` installed the archive.
- With no `GABIER_*` variable, `gabier --version` printed `gabier 0.1.0-rc.2` and `gabier diagnostics` answered the
  type mismatch of the file. The jdtls that ran was the `jdtls/` of the mise install.
- The switch of version was proven by the worker only, with a fake release API serving two local packages
  (`0.1.0-rc.9` to `0.1.0-rc.8`: the cli, the daemon and jdtls changed together). It was not proven on GitHub,
  which would need a second tag. The user chose to publish `v0.1.0` now and to see this at `v0.2.0`.

Review (once), no blocking finding:
- The findings on `JdtlsLocation` went to `02-11-2026-2324-the-jdtls-lookup-follows-home.md`, on hold:
  - the shipped jdtls is taken next to any jar, not only one in a `lib/` folder;
  - a code source that is not a `file:` location throws instead of falling through;
  - the 2-argument `launcher` has no test of its wiring;
  - the refusal does not name the shipped place.
- The small ones were fixed in `38c120f`, with those of the release install script.

Tokens:
- worker: 143636
- reviewer: 69225
- supervisor: 62 calls, 49617 output, 207614 input + cache write (13520107 cache reads, apart). This covers the
  supervisor session from the launch of the worker to the closing, with the follow-up `38c120f`, the push of
  `v0.1.0-rc.2` and its proof. Exchanges with the user: 2 messages.
