# The jdtls data folder of a killed daemon does not stay behind

Opened: 02-11-2026 09:01
Depends on: 02-11-2026-0900-jdtls-shares-library-indexes-between-projects.md

The real bug met while looking at the jdtls instances: `/tmp` is a tmpfs (RAM, 14.5 Go), and the `-data` folder of a
daemon that was killed without a clean stop stays in it (`gabier-jdtls-data-*`: 42 Mo from 30-10 and 700 Ko today, against
227 Mo for a live one). `scripts/stop-stray-daemons.sh` stops such daemons but removes no folder.

Done when:
- [ ] When it starts jdtls, the daemon removes the `gabier-jdtls-data-*` folders of the user that no live jdtls uses (the
      lock of the Eclipse workspace, `.metadata/.lock`, says whether it is held). It never touches the folder of a running
      jdtls, nor one that is not the user's (tests, red first, for each case).
- [ ] A `-data` kept by the option of the ticket "the jdtls workspace of a project can be kept between daemons" is never
      concerned (it is outside the temporary prefix).
- [ ] `docs/daemon.md` says it. `bin/ci` passes.
