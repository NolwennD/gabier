package eu.gabier.cli.infrastructure.primary;

import eu.gabier.cli.domain.Daemon;
import java.io.PrintStream;

/**
 * One run of a command: its arguments, whether the output is JSON, where it is printed, and the way to the daemon.
 *
 * @param arguments the words of the command line, the command first
 * @param json whether {@code --json} was given
 * @param out where the result is printed
 * @param daemon the way to the daemon of the project
 */
record Invocation(Arguments arguments, boolean json, PrintStream out, Daemon daemon) {}
