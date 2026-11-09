package eu.gabier.cli.infrastructure.primary;

import eu.gabier.cli.application.OperationRunner;
import eu.gabier.cli.domain.Daemon;
import java.io.InputStream;
import java.io.PrintStream;

/**
 * One run of a command: its arguments, whether the output is JSON, where it is printed, the way to run an operation and
 * the way to the daemon, for the commands that do not run an operation ({@code start}, {@code status}, {@code stop}).
 *
 * @param arguments the words of the command line, the command first
 * @param json whether {@code --json} was given
 * @param out where the result is printed
 * @param operations how an operation is run, the one entry point that any front end uses
 * @param daemon the way to the daemon of the project
 * @param in what is typed or piped in, for the commands that read it ({@code mcp})
 * @param err where a command logs what it must not print on its output, for the commands that run for long ({@code
 *     mcp})
 */
record Invocation(
        Arguments arguments,
        boolean json,
        PrintStream out,
        OperationRunner operations,
        Daemon daemon,
        InputStream in,
        PrintStream err) {}
