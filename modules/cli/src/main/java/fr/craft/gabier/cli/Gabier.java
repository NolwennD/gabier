package fr.craft.gabier.cli;

import fr.craft.gabier.protocol.ChangedFiles;
import fr.craft.gabier.protocol.DaemonStatus;
import fr.craft.gabier.protocol.ExitCode;
import fr.craft.gabier.protocol.FileDiagnostics;
import fr.craft.gabier.protocol.Problem;
import fr.craft.gabier.protocol.ProblemMapper;
import fr.craft.gabier.protocol.ProtocolVersion;
import fr.craft.gabier.protocol.RpcResponse;
import fr.craft.gabier.protocol.SocketLocation;
import fr.craft.gabier.protocol.TerminalText;
import fr.craft.gabier.protocol.UserId;
import fr.craft.gabier.shared.generation.ExcludeFromGeneratedCodeCoverage;
import java.io.PrintStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

public final class Gabier {

    private static final String VERSION_COMMAND = "--version";
    private static final String HELP_OPTION = "--help";
    private static final String SHORT_HELP_OPTION = "-h";
    private static final String DRY_RUN_OPTION = "--dry-run";
    private static final String TOKEN_OPTION = "--token";
    private static final String JSON_OPTION = "--json";

    private Gabier() {}

    @ExcludeFromGeneratedCodeCoverage(
            reason = "The entry point ends in System.exit, which a unit test cannot cover: "
                    + "all its logic is in run, which is tested")
    public static void main(String[] args) {
        System.exit(run(args, System.getenv(), System.out, System.err));
    }

    static int run(String[] args, Map<String, String> environment, PrintStream out, PrintStream err) {
        var arguments = List.of(args);
        var json = arguments.contains(JSON_OPTION);
        try {
            execute(
                    arguments.stream()
                            .filter(argument -> !argument.equals(JSON_OPTION))
                            .toList(),
                    environment,
                    json,
                    out);

            return ExitCode.SUCCESS.value();
        } catch (DaemonRefusal refusal) {
            return report(refusal.problem(), json, err);
        } catch (RuntimeException e) {
            return fail(e, json, err);
        }
    }

    private static void execute(
            List<String> arguments, Map<String, String> environment, boolean json, PrintStream out) {
        var command = arguments.isEmpty() ? Optional.<Command>empty() : Command.named(arguments.getFirst());
        if (command.isEmpty()) {
            standalone(arguments, json, out);

            return;
        }
        // A switch expression: the compiler refuses a command of the enum that is not handled here.
        Runnable action = switch (command.get()) {
            case DIAGNOSTICS ->
                () -> diagnostics(starting(environment), exactly(arguments, 2).getLast(), json, out);
            case RENAME -> () -> rename(starting(environment), arguments, json, out);
            case ORGANIZE_IMPORTS -> () -> organizeImports(starting(environment), arguments, json, out);
            case EXTRACT_VARIABLE -> () -> extractVariable(starting(environment), arguments, json, out);
            case STATUS -> () -> status(waiting(environment, arguments), json, out);
            case STOP -> () -> stop(waiting(environment, arguments), json, out);
            case HELP -> () -> help(arguments, json, out);
        };
        action.run();
    }

    /** The arguments that name no command: {@code --version}, {@code --help} and {@code -h}, alone, else a usage error. */
    private static void standalone(List<String> arguments, boolean json, PrintStream out) {
        if (arguments.size() == 1) {
            switch (arguments.getFirst()) {
                case VERSION_COMMAND -> {
                    out.println("gabier " + ProtocolVersion.CURRENT);

                    return;
                }
                case HELP_OPTION, SHORT_HELP_OPTION -> {
                    out.println(HelpText.index(json));

                    return;
                }
                default -> {
                    // A usage error, below.
                }
            }
        }

        throw UsageException.unsupportedArguments(arguments);
    }

    /**
     * {@code help} lists the commands, {@code help <command>} explains one. It never talks to the daemon, and starts none:
     * the text is the one of {@link Command}.
     */
    private static void help(List<String> arguments, boolean json, PrintStream out) {
        if (arguments.size() == 1) {
            out.println(HelpText.index(json));

            return;
        }
        var name = exactly(arguments, 2).getLast();
        var command = Command.named(name).orElseThrow(() -> UsageException.unknownCommand(name, HelpText.names()));
        out.println(HelpText.of(command, json));
    }

    /** The arguments, when there are that many (the command included); else a usage error. */
    private static List<String> exactly(List<String> arguments, int count) {
        if (arguments.size() != count) {
            throw UsageException.unsupportedArguments(arguments);
        }

        return arguments;
    }

    private static Path socket(Map<String, String> environment) {
        return SocketLocation.resolve(environment, UserId.current());
    }

    /**
     * The way to the daemon of a request command: it answers, or it is started (the cli never leaves a request unanswered
     * for want of a daemon).
     */
    private static Supplier<DaemonClient> starting(Map<String, String> environment) {
        var socket = socket(environment);
        var launcher = DaemonLauncher.of(environment);

        return () -> launcher.connect(socket);
    }

    /**
     * The way to the daemon of {@code status} and {@code stop}, which take no argument: it answers or it is not running,
     * and none is started.
     */
    private static Supplier<DaemonClient> waiting(Map<String, String> environment, List<String> arguments) {
        exactly(arguments, 1);
        var socket = socket(environment);

        return () -> DaemonClient.connect(socket);
    }

    /**
     * One line per problem, {@code file:line:column: severity: message}, the file as the user typed it and the position
     * from 1 as an editor shows it (the daemon counts from 0); with {@code --json}, the result as the daemon gave it.
     * The daemon gets the absolute path, not cleaned: its working directory is not the one of this command, and only the
     * daemon cleans paths (it collapses {@code .} and {@code ..}, it follows no link). The line is neutralised
     * as the text of a problem is ({@link TerminalText}): the message quotes source text and the file is typed, and
     * neither may drive the terminal or split the line.
     */
    private static void diagnostics(Supplier<DaemonClient> daemon, String file, boolean json, PrintStream out) {
        var absolute = Path.of(file).toAbsolutePath();
        var result = ask(daemon, Command.DIAGNOSTICS.commandName(), Map.of("file", absolute.toString()), false);
        var diagnostics = FileDiagnostics.of(result.result());
        if (json) {
            out.println(diagnostics.toJson());

            return;
        }
        diagnostics
                .diagnostics()
                .forEach(diagnosis -> out.println(TerminalText.neutralise(file + ":" + (diagnosis.line() + 1) + ":"
                        + (diagnosis.column() + 1) + ": " + diagnosis.severity().text() + ": "
                        + diagnosis.message())));
    }

    /**
     * {@code rename [--dry-run] <file> <line> <column> <new-name>} or {@code rename [--dry-run] --token <file>
     * <identifier> <new-name>}: the options may stand anywhere after the command. The position is from 1, as
     * {@code diagnostics} shows it, and goes to the daemon from 0. The text lists the files that changed, then the
     * diff, with the same neutralisation as every text that comes from the project; {@code --json} gives the result as
     * the daemon gave it.
     */
    private static void rename(Supplier<DaemonClient> daemon, List<String> arguments, boolean json, PrintStream out) {
        var words = arguments.subList(1, arguments.size());
        var dryRun = words.contains(DRY_RUN_OPTION);
        var token = words.contains(TOKEN_OPTION);
        var operands = words.stream()
                .filter(word -> !word.equals(DRY_RUN_OPTION) && !word.equals(TOKEN_OPTION))
                .toList();
        var params = new HashMap<String, String>();
        if (token && operands.size() == 3) {
            params.put("file", Path.of(operands.get(0)).toAbsolutePath().toString());
            params.put("token", operands.get(1));
        } else if (!token && operands.size() == 4) {
            params.put("file", Path.of(operands.get(0)).toAbsolutePath().toString());
            params.put("line", zeroBased(arguments, operands.get(1)));
            params.put("column", zeroBased(arguments, operands.get(2)));
        } else {
            throw UsageException.unsupportedArguments(arguments);
        }
        params.put("newName", operands.getLast());
        params.put("dryRun", String.valueOf(dryRun));
        var changed = ChangedFiles.of(ask(daemon, token ? "rename-token" : Command.RENAME.commandName(), params, false)
                .result());
        print(changed, dryRun, json, out);
    }

    /**
     * {@code organize-imports [--dry-run] <file>}: the option may stand anywhere after the command. It answers as {@code
     * rename} does, and says {@code nothing to change} (as text) for a file whose imports are already in order.
     */
    private static void organizeImports(
            Supplier<DaemonClient> daemon, List<String> arguments, boolean json, PrintStream out) {
        var words = arguments.subList(1, arguments.size());
        var dryRun = words.contains(DRY_RUN_OPTION);
        var operands =
                words.stream().filter(word -> !word.equals(DRY_RUN_OPTION)).toList();
        if (operands.size() != 1) {
            throw UsageException.unsupportedArguments(arguments);
        }
        var params = Map.of(
                "file", Path.of(operands.getFirst()).toAbsolutePath().toString(), "dryRun", String.valueOf(dryRun));
        var changed = ChangedFiles.of(ask(daemon, Command.ORGANIZE_IMPORTS.commandName(), params, false)
                .result());
        if (!json && changedText(changed).isEmpty()) {
            if (dryRun) {
                out.println("dry run, nothing written");
            }
            out.println("nothing to change");

            return;
        }
        print(changed, dryRun, json, out);
    }

    /**
     * {@code extract-variable [--dry-run] <file> <line>:<column> <line>:<column> <name>}: the option may stand anywhere
     * after the command. The selection is the expression to extract, from its first character to the position just
     * after its last (the end is excluded), each position from 1 as {@code diagnostics} shows it; the daemon gets them
     * from 0. It answers as {@code rename} does.
     */
    private static void extractVariable(
            Supplier<DaemonClient> daemon, List<String> arguments, boolean json, PrintStream out) {
        var words = arguments.subList(1, arguments.size());
        var dryRun = words.contains(DRY_RUN_OPTION);
        var operands =
                words.stream().filter(word -> !word.equals(DRY_RUN_OPTION)).toList();
        if (operands.size() != 4) {
            throw UsageException.unsupportedArguments(arguments);
        }
        var start = lineAndColumn(arguments, operands.get(1));
        var end = lineAndColumn(arguments, operands.get(2));
        var params = new HashMap<String, String>();
        params.put("file", Path.of(operands.getFirst()).toAbsolutePath().toString());
        params.put("startLine", start.getFirst());
        params.put("startColumn", start.getLast());
        params.put("endLine", end.getFirst());
        params.put("endColumn", end.getLast());
        params.put("name", operands.getLast());
        params.put("dryRun", String.valueOf(dryRun));
        var changed = ChangedFiles.of(ask(daemon, Command.EXTRACT_VARIABLE.commandName(), params, false)
                .result());
        print(changed, dryRun, json, out);
    }

    /** {@code line:column} as the user counts it, from 1, as the line and the column the daemon counts, from 0. */
    private static List<String> lineAndColumn(List<String> arguments, String position) {
        var parts = position.split(":", -1);
        if (parts.length != 2) {
            throw UsageException.unsupportedArguments(arguments);
        }

        return List.of(zeroBased(arguments, parts[0]), zeroBased(arguments, parts[1]));
    }

    /** The result of the daemon as it gave it with {@code --json}; else the dry run said, then {@link #changedText}. */
    private static void print(ChangedFiles changed, boolean dryRun, boolean json, PrintStream out) {
        if (json) {
            out.println(changed.toJson());

            return;
        }
        if (dryRun) {
            out.println("dry run, nothing written");
        }
        changedText(changed).forEach(line -> out.println(TerminalText.neutralise(line)));
    }

    /** A position as the user counts it, from 1, as the daemon counts it, from 0. */
    private static String zeroBased(List<String> arguments, String position) {
        try {
            int value = Integer.parseInt(position);
            if (value >= 1) {
                return String.valueOf(value - 1);
            }
        } catch (NumberFormatException notANumber) {
            // The usage error below says it.
        }

        throw UsageException.unsupportedArguments(arguments);
    }

    /** One line per file changed, a blank line, then the diff; nothing for a result that holds nothing. */
    private static List<String> changedText(ChangedFiles changed) {
        var lines = new ArrayList<String>();
        changed.modified().forEach(file -> lines.add("modified " + file));
        changed.created().forEach(file -> lines.add("created " + file));
        changed.renamed().forEach(moved -> lines.add("renamed " + moved.from() + " -> " + moved.to()));
        changed.deleted().forEach(file -> lines.add("deleted " + file));
        if (!changed.diff().isEmpty()) {
            lines.add("");
            changed.diff().lines().forEach(lines::add);
        }

        return lines;
    }

    private static void status(Supplier<DaemonClient> daemon, boolean json, PrintStream out) {
        var result = ask(daemon, Command.STATUS.commandName(), false);
        var status = DaemonStatus.of(result.result());
        out.println(json ? status.toJson() : statusText(status));
    }

    /**
     * The version, the project and the reason jdtls failed come from the daemon, which has them from jdtls: the line
     * is neutralised ({@link TerminalText}).
     */
    private static String statusText(DaemonStatus status) {
        var failure = status.jdtlsFailure().map(reason -> " (" + reason + ")").orElse("");
        return TerminalText.neutralise("gabier-daemon " + status.version() + ", pid " + status.pid() + ", up "
                + status.uptime().toSeconds() + "s, project " + status.project() + ", jdtls "
                + status.jdtls().text() + failure);
    }

    private static void stop(Supplier<DaemonClient> daemon, boolean json, PrintStream out) {
        ask(daemon, Command.STOP.commandName(), true);
        out.println(json ? "{\"stopped\":true}" : "gabier-daemon stopped");
    }

    /** The result of the method, or the refusal of the daemon thrown. Waits for the daemon to hang up when asked. */
    private static RpcResponse.Success ask(Supplier<DaemonClient> daemon, String method, boolean awaitEnd) {
        return ask(daemon, method, Map.of(), awaitEnd);
    }

    private static RpcResponse.Success ask(
            Supplier<DaemonClient> daemon, String method, Map<String, String> params, boolean awaitEnd) {
        try (var client = daemon.get()) {
            var response = client.call(method, params);
            if (response instanceof RpcResponse.Failure failure) {
                throw new DaemonRefusal(failure.problem());
            }
            if (awaitEnd) {
                client.awaitEnd();
            }

            return (RpcResponse.Success) response;
        }
    }

    /** Reports the failure as a problem object on the standard error, as JSON or as text, and gives the exit code. */
    static int fail(RuntimeException failure, boolean json, PrintStream err) {
        return report(ProblemMapper.toProblem(failure), json, err);
    }

    private static int report(Problem problem, boolean json, PrintStream err) {
        err.println(json ? problem.toJson() : problem.toText());

        return ExitCode.of(problem).value();
    }
}
