package fr.craft.gabier.cli;

import fr.craft.gabier.shared.error.domain.Assert;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * What {@code gabier help} says about a command, written once, in the class of its {@link Capability}: its name, a
 * one-line summary, the usage, an example, the notes to read before the call (none for most commands) and the refusals
 * it can answer. A command is built with {@link #called(String)}, which asks for the parts one after the other, so that
 * one left out does not compile, and ends by choosing the family of its refusals. A new optional datum gets a default
 * in the builder and a {@code withXxx} method here, which only the commands that need it call.
 *
 * @param commandName the word that names it on the command line
 * @param summary one line: what it does
 * @param usage the command line, one line per form
 * @param example an example of a call and of its answer, the call on a line that starts with {@code $ }
 * @param notes what a person must know before running the command, none (empty) for most of them
 * @param refusals the refusals, each as the key of its problem, a colon and what it means
 */
record Command(String commandName, String summary, String usage, String example, String notes, List<String> refusals) {

    Command {
        Assert.field("commandName", commandName).notBlank().noWhitespace();
        Assert.notBlank("summary", summary);
        Assert.field("usage", usage)
                .matchesPattern(Pattern.compile("(?s).*gabier \\[--json] " + Pattern.quote(commandName) + "\\b.*"));
        Assert.field("example", example).matchesPattern(Pattern.compile("(?s)\\$ gabier .*"));
        Assert.notNull("notes", notes);
        Assert.field("refusals", refusals).notEmpty().noNullElement();
        refusals.forEach(refusal -> Assert.notBlank("refusal", refusal));
        refusals = List.copyOf(refusals);
    }

    /** Starts the declaration of the command that this word names on the command line. */
    static SummaryStep called(String commandName) {
        return new Builder(commandName);
    }

    /** The same command with notes, printed by {@code help} between the example and the refusals. */
    Command withNotes(String notes) {
        Assert.notBlank("notes", notes);

        return new Command(commandName, summary, usage, example, notes, refusals);
    }

    /** The step that asks for the summary. */
    interface SummaryStep {

        /** One line: what the command does. */
        UsageStep summary(String summary);
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

    /** The last step: the family of refusals decides which refusals every command of that family answers. */
    interface RefusalsStep {

        /** A command that asks the daemon, starting it if need be: its own refusals, then the ones of every such command. */
        Command asksTheDaemon(String... own);

        /** A command that asks a daemon that is running and never starts one. */
        Command asksARunningDaemon();

        /** A command that never talks to the daemon: exactly these refusals. */
        Command standalone(String... refusals);
    }

    private static final class Builder implements SummaryStep, UsageStep, ExampleStep, RefusalsStep {

        private final String commandName;
        private String summary = "";
        private String usage = "";
        private String example = "";

        private Builder(String commandName) {
            this.commandName = commandName;
        }

        @Override
        public UsageStep summary(String summary) {
            this.summary = summary;

            return this;
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
        public Command asksTheDaemon(String... own) {
            return standalone(Stream.concat(
                            Stream.of(own), Stream.of(Refusal.USAGE, Refusal.START_FAILED, Refusal.CONNECTION_FAILED))
                    .toArray(String[]::new));
        }

        @Override
        public Command asksARunningDaemon() {
            return standalone(Refusal.NOT_RUNNING, Refusal.USAGE, Refusal.CONNECTION_FAILED);
        }

        @Override
        public Command standalone(String... refusals) {
            return new Command(commandName, summary, usage, example, "", List.of(refusals));
        }
    }
}
