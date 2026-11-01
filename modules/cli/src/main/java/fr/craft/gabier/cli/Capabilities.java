package fr.craft.gabier.cli;

/** The capability of each command. */
final class Capabilities {

    private Capabilities() {}

    /** The capability that runs this command. The compiler refuses a command of the enum that is not handled here. */
    static Capability of(Command command) {
        return switch (command) {
            case DIAGNOSTICS -> new Diagnostics();
            case RENAME -> new Rename();
            case ORGANIZE_IMPORTS -> new OrganizeImports();
            case EXTRACT_VARIABLE -> new ExtractVariable();
            case EXTRACT_METHOD -> new ExtractMethod();
            case MOVE_TYPE_TO_FILE -> new MoveTypeToFile();
            case STATUS -> new Status();
            case STOP -> new Stop();
            case HELP -> new Help();
        };
    }
}
