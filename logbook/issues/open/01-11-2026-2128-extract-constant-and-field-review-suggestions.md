# Extract-constant and extract-field refusals say what they know

Opened: 01-11-2026 21:28
On hold: findings of the review of 95f3926 and e3669d3, none breaks a `Done when` point; lifted by the user or when a use makes one matter.

The refusals and the claims of `extract-constant` and `extract-field` are wider than what is proven or said.

Done when:
- [ ] `JdtlsLanguageServer.java:551-558`: a field renamed to a name that collides with a parameter (`extract-field` of `"count: "` as `count` in `print(int count)`) is refused, shown by a scenario replayed on jdtls; or the claim that the line check guards it is dropped.
- [ ] `JdtlsLanguageServer.java:429`: the message "jdtls extracted the variable but did not say where its name is" names the operation (constant, field, method), not only the variable.
- [ ] `JavaLanguageServer.java:55` and `docs/commands/extract-constant.md`: an expression reading an instance member is refused, shown by a scenario replayed on jdtls; or the claim is removed.
- [ ] `bin/ci.d/40-native`: the added comment lines wrap at 120 columns.
