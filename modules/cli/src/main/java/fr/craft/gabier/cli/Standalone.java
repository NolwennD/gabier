package fr.craft.gabier.cli;

import fr.craft.gabier.protocol.ProtocolVersion;
import java.io.PrintStream;
import java.util.List;

/** The arguments that name no command: {@code --version}, {@code --help} and {@code -h}, alone, else a usage error. */
final class Standalone {

    private static final String VERSION_OPTION = "--version";
    private static final String HELP_OPTION = "--help";
    private static final String SHORT_HELP_OPTION = "-h";

    private Standalone() {}

    static void run(List<String> arguments, boolean json, PrintStream out) {
        if (arguments.size() == 1) {
            switch (arguments.getFirst()) {
                case VERSION_OPTION -> {
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
}
