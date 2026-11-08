package eu.gabier.cli.domain;

import java.nio.file.Path;

/**
 * The answer to a request that may have started the daemon.
 *
 * @param result what the daemon answered
 * @param daemonStarted whether the daemon was not running and this call started it
 * @param log where the output of a daemon that this call starts goes
 */
public record DaemonStart(DaemonReply result, boolean daemonStarted, Path log) {}
