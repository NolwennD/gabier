package eu.gabier.cli.domain;

import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * What the daemon told, on its own, to a client that asked to follow something: a method that names what it is (the
 * problems of some files, a notice for the person who reads, the end of the subscription) and the members of its
 * params, which the command decodes.
 *
 * @param method what the notification is
 * @param params the members of its params, by name
 */
public record DaemonNotification(String method, Map<String, @Nullable Object> params) {}
