package eu.gabier.cli;

import java.nio.file.Path;
import java.util.List;

/**
 * The words of a command line, the command included ({@code --json} left out), and the rules to read them: the words
 * after the command, the options that may stand anywhere, the operands that are left, how many there must be, a file as
 * an absolute path and a position as the user counts it, from 1. Every refusal is the one usage error, which names the
 * words given.
 *
 * @param all the words, the command first
 * @param operands the words the command reads as its operands
 */
record Arguments(List<String> all, List<String> operands) {

    static final String DRY_RUN = "--dry-run";
    static final String TOKEN = "--token";
    static final String HELP = "--help";
    static final String SHORT_HELP = "-h";

    Arguments {
        all = List.copyOf(all);
        operands = List.copyOf(operands);
    }

    /** The words of a command line whose first word names the command; every other word is an operand. */
    static Arguments of(List<String> all) {
        return new Arguments(all, all.subList(1, all.size()));
    }

    /** Whether {@code --help} or {@code -h} stands anywhere after the command: the help of the command is asked. */
    boolean wantsHelp() {
        return has(HELP) || has(SHORT_HELP);
    }

    /** Whether {@code --dry-run} stands anywhere after the command. */
    boolean dryRun() {
        return has(DRY_RUN);
    }

    /** Whether this option stands anywhere after the command. */
    boolean has(String option) {
        return all.subList(1, all.size()).contains(option);
    }

    /** These words, when there are that many operands as they stand; else the usage error. */
    Arguments exactly(int count) {
        if (operands.size() != count) {
            throw UsageException.unsupportedArguments(all);
        }

        return this;
    }

    /**
     * The operands once {@code --dry-run} and these options are taken out, when there are that many; else the usage
     * error.
     */
    Arguments operands(int count, String... options) {
        var ignored = List.of(options);

        return new Arguments(
                        all,
                        operands.stream()
                                .filter(word -> !word.equals(DRY_RUN) && !ignored.contains(word))
                                .toList())
                .exactly(count);
    }

    /**
     * The operands once {@code --dry-run} is taken out, when there are at least that many; else the usage error. For a
     * command that takes a list of files and then a last operand.
     */
    Arguments atLeast(int count) {
        var withoutDryRun = without(DRY_RUN);
        if (withoutDryRun.operands.size() < count) {
            throw UsageException.unsupportedArguments(all);
        }

        return withoutDryRun;
    }

    /**
     * These words with this option taken out of the operands, and nothing else: unlike {@link #operands(int, String...)},
     * {@code --dry-run} stays an operand, so that a command with no dry run refuses it.
     */
    Arguments without(String option) {
        return new Arguments(
                all, operands.stream().filter(word -> !word.equals(option)).toList());
    }

    /** The operand at this index. */
    String operand(int index) {
        return operands.get(index);
    }

    /**
     * The operand at this index as a file: the absolute path, not cleaned. The daemon's working directory is not the one
     * of the cli, and only the daemon cleans paths.
     */
    Path file(int index) {
        return Path.of(operand(index)).toAbsolutePath();
    }

    /** The operand at this index as {@code line:column}, each from 1. */
    Position position(int index) {
        var parts = operand(index).split(":", -1);
        if (parts.length != 2) {
            throw UsageException.unsupportedArguments(all);
        }

        return new Position(zeroBased(parts[0]), zeroBased(parts[1]));
    }

    /** The operands at these two indexes as a line and a column, each from 1. */
    Position position(int lineIndex, int columnIndex) {
        return new Position(zeroBased(operand(lineIndex)), zeroBased(operand(columnIndex)));
    }

    private int zeroBased(String number) {
        try {
            int value = Integer.parseInt(number);
            if (value >= 1) {
                return value - 1;
            }
        } catch (NumberFormatException notANumber) {
            // The usage error below says it.
        }

        throw UsageException.unsupportedArguments(all);
    }

    /**
     * A line and a column counted from 0, as the daemon counts them.
     *
     * @param line the line
     * @param column the column
     */
    record Position(int line, int column) {}
}
