package fr.craft.gabier.cli;

/**
 * {@code extract-method [--dry-run] <file> <line>:<column> <line>:<column> <name>}, as {@code extract-variable}: the
 * selection is the statements, or the expression, to extract into a method of that name.
 */
final class ExtractMethod implements Capability {

    @Override
    public Command command() {
        return Command.EXTRACT_METHOD;
    }

    @Override
    public void run(Invocation invocation) {
        new ExtractSelection(command()).run(invocation);
    }
}
