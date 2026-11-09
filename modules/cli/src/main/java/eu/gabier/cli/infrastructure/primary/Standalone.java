package eu.gabier.cli.infrastructure.primary;

import eu.gabier.protocol.ProtocolVersion;
import java.io.PrintStream;
import java.util.List;
import java.util.Optional;

/** The arguments that name no command: {@code --version}, {@code --help} and {@code -h}, alone, else a usage error. */
final class Standalone {

    private static final String VERSION_OPTION = "--version";

    private Standalone() {}

    static void run(List<String> arguments, boolean json, PrintStream out) {
        if (arguments.size() == 1) {
            switch (arguments.getFirst()) {
                case VERSION_OPTION -> {
                    out.println("gabier " + ProtocolVersion.CURRENT);

                    return;
                }
                case Arguments.HELP, Arguments.SHORT_HELP -> {
                    out.println(HelpText.index(json));

                    return;
                }
                default -> {
                    // A usage error, below.
                }
            }
        }

        var meant = arguments.isEmpty() ? Optional.<Capability>empty() : Capabilities.nearestTo(arguments.getFirst());
        if (meant.isPresent()) {
            throw UsageException.unknownCommandTypo(arguments, meant.get().command());
        }

        throw UsageException.noCommand(arguments);
    }
}
