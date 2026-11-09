package eu.gabier.cli.infrastructure.primary;

import eu.gabier.cli.domain.Operation;
import eu.gabier.shared.error.domain.Assert;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * What {@code gabier help} says about a command: its name, the usage, an example, and the operations of the daemon it
 * runs ({@link Operation}, which hold the summary, the notes, the refusals and the parameters, written once in the
 * domain). The command line keeps only what is its own: how a person types the command, and the refusals that a
 * command line meets whatever it asks (a usage error, a daemon that cannot be reached). A command that never reaches
 * the daemon ({@code help}, {@code init-agent}) has no operation, and holds its own summary, notes and refusals. A
 * command is built with {@link #called(String)}, which asks for the parts one after the other, so that one left out
 * does not compile, and ends by choosing the family of its refusals.
 *
 * @param commandName the word that names it on the command line
 * @param usage the command line, one line per form
 * @param example an example of a call and of its answer, the call on a line that starts with {@code $ }
 * @param operations the operations of the daemon it runs, in the order of its usage lines; none for a command of the
 *     cli alone
 * @param ownSummary the summary of a command that has no operation, else empty
 * @param ownNotes the notes of the command line itself, which follow those of its operations; all of them for a command
 *     that has no operation; often empty
 * @param commandLineRefusals the refusals of the command line, which follow those of its operations; all of them for a
 *     command that has no operation
 */
record Command(
        String commandName,
        String usage,
        String example,
        List<Operation> operations,
        String ownSummary,
        String ownNotes,
        List<String> commandLineRefusals) {

    Command {
        Assert.field("commandName", commandName).notBlank().noWhitespace();
        Assert.field("usage", usage)
                .matchesPattern(Pattern.compile("(?s).*gabier \\[--json] " + Pattern.quote(commandName) + "\\b.*"));
        Assert.field("example", example).matchesPattern(Pattern.compile("(?s)\\$ gabier .*"));
        Assert.field("operations", operations).notNull().noNullElement();
        Assert.notNull("ownSummary", ownSummary);
        Assert.notNull("ownNotes", ownNotes);
        Assert.field("commandLineRefusals", commandLineRefusals).notEmpty().noNullElement();
        commandLineRefusals.forEach(refusal -> Assert.notBlank("refusal", refusal));
        if (operations.isEmpty()) {
            Assert.notBlank("ownSummary", ownSummary);
        } else if (!ownSummary.isEmpty()) {
            throw new IllegalArgumentException("The command " + commandName + " has operations: they say what it does");
        }
        operations = List.copyOf(operations);
        commandLineRefusals = List.copyOf(commandLineRefusals);
    }

    /** Starts the declaration of the command that this word names on the command line. */
    static UsageStep called(String commandName) {
        return new Builder(commandName);
    }

    /** One line: what it does, as its first operation says it. */
    String summary() {
        return operations.isEmpty() ? ownSummary : operations.getFirst().summary();
    }

    /**
     * What a person must know before running it: the notes of its operations, one after the other, then the notes of
     * the command line itself (what only a person who types it reads: an option, the text it prints); often none.
     */
    String notes() {
        return Stream.concat(operations.stream().map(Operation::notes), Stream.of(ownNotes))
                .filter(notes -> !notes.isEmpty())
                .collect(Collectors.joining("\n"));
    }

    /**
     * The refusals, each as the key of its problem, a colon and what it means: those of its operations, where the ones
     * of a later operation that its first one does not have are put before the first refusal that both have, then those
     * of the command line.
     */
    List<String> refusals() {
        var refusals = new ArrayList<String>();
        for (var operation : operations) {
            mergeInto(refusals, operation.refusals());
        }
        refusals.addAll(commandLineRefusals);

        return List.copyOf(refusals);
    }

    /**
     * The same command with notes of the command line, printed by {@code help} between the example and the refusals,
     * after those of its operations.
     */
    Command withNotes(String notes) {
        Assert.notBlank("notes", notes);

        return new Command(commandName, usage, example, operations, ownSummary, notes, commandLineRefusals);
    }

    private static void mergeInto(List<String> merged, List<String> refusals) {
        var pending = new ArrayList<String>();
        for (var refusal : refusals) {
            var at = merged.indexOf(refusal);
            if (at < 0) {
                pending.add(refusal);
            } else {
                merged.addAll(at, pending);
                pending.clear();
            }
        }
        merged.addAll(pending);
    }

    /** The step that asks for the usage. */
    interface UsageStep {

        /** The command line, one line per form, each starting with {@code gabier [--json] <command>}. */
        ExampleStep usage(String usage);
    }

    /** The step that asks for the example. */
    interface ExampleStep {

        /** A call, on a line that starts with {@code $ gabier }, and its answer. */
        RefusalsStep example(String example);
    }

    /** The last step: the family of the command decides which refusals of the command line it answers. */
    interface RefusalsStep {

        /** A command that asks the daemon, starting it if need be, for these operations. */
        Command asksTheDaemon(Operation... operations);

        /** A command that asks a daemon that is running for this operation, and never starts one. */
        Command asksARunningDaemon(Operation operation);

        /** A command that never talks to the daemon: what it does, and exactly these refusals. */
        Command standalone(String summary, String... refusals);

        /**
         * A command that serves the operations of the daemon to another front end, which calls them as it likes: the
         * calls start the daemon and meet its refusals, and tell them to their caller, not to the command line. The
         * command itself ends only with a usage error.
         */
        Command servesTheOperations(String summary);
    }

    private static final class Builder implements UsageStep, ExampleStep, RefusalsStep {

        private final String commandName;
        private String usage = "";
        private String example = "";

        private Builder(String commandName) {
            this.commandName = commandName;
        }

        @Override
        public ExampleStep usage(String usage) {
            this.usage = usage;

            return this;
        }

        @Override
        public RefusalsStep example(String example) {
            this.example = example;

            return this;
        }

        @Override
        public Command asksTheDaemon(Operation... operations) {
            return new Command(
                    commandName,
                    usage,
                    example,
                    List.of(operations),
                    "",
                    "",
                    List.of(Refusal.USAGE, Refusal.START_FAILED, Refusal.CONNECTION_FAILED));
        }

        @Override
        public Command asksARunningDaemon(Operation operation) {
            return new Command(
                    commandName,
                    usage,
                    example,
                    List.of(operation),
                    "",
                    "",
                    List.of(Refusal.NOT_RUNNING, Refusal.USAGE, Refusal.CONNECTION_FAILED));
        }

        @Override
        public Command standalone(String summary, String... refusals) {
            return new Command(commandName, usage, example, List.of(), summary, "", List.of(refusals));
        }

        @Override
        public Command servesTheOperations(String summary) {
            return standalone(summary, Refusal.USAGE);
        }
    }
}
