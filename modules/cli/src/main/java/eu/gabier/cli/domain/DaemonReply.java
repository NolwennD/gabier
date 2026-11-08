package eu.gabier.cli.domain;

import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * What the daemon answered to a request that succeeded: the members of its result, which the command decodes.
 *
 * @param result the members of the result, by name
 */
public record DaemonReply(Map<String, @Nullable Object> result) {}
