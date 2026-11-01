package fr.craft.gabier.cli;

/**
 * {@code extract-variable [--dry-run] <file> <line>:<column> <line>:<column> <name>}: the option may stand anywhere
 * after the command. The selection is the expression to extract, from its first character to the position just after
 * its last (the end is excluded). It answers as {@code rename} does.
 */
final class ExtractVariable implements Capability {

    @Override
    public Command command() {
        return Command.EXTRACT_VARIABLE;
    }

    @Override
    public void run(Invocation invocation) {
        new ExtractSelection(command()).run(invocation);
    }
}
