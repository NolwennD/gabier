# The release version is named for what it is

Opened: 02-11-2026 22:09
On hold: findings of the review of `a-tag-publishes-a-release-with-the-binaries`; they break no `Done when` point.

`ProtocolVersion` (`modules/protocol`), now `record ProtocolVersion(String value)`, holds the release version of gabier,
not a protocol version, and the tests that name it say "protocol version" (`GabierTest.java:17`, `MainTest.java:71`).
`ProtocolVersionTest.isTheVersionTheBuildWasGiven` compares the resource with a system property that Gradle fills from the
same provider, so a filter that always wrote `0.0.0-dev` would still pass on a default build.

Done when:
- the type and the tests are renamed through `gabier rename` for the release version (the name in the `## Resolution`);
- a test fails when the version given to the build does not reach the resource (a build run with a version other than
  the default); `bin/ci` passes.
