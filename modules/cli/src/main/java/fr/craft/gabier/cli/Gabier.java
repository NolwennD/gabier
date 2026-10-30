package fr.craft.gabier.cli;

import fr.craft.gabier.protocol.ProtocolVersion;
import fr.craft.gabier.shared.generation.ExcludeFromGeneratedCodeCoverage;
import java.io.PrintStream;

public final class Gabier {

    private static final String VERSION_OPTION = "--version";
    private static final int OK = 0;
    private static final int USAGE_ERROR = 2;

    private Gabier() {}

    @ExcludeFromGeneratedCodeCoverage(
            reason = "The entry point ends in System.exit, which a unit test cannot cover: "
                    + "all its logic is in run, which is tested")
    public static void main(String[] args) {
        System.exit(run(args, System.out, System.err));
    }

    static int run(String[] args, PrintStream out, PrintStream err) {
        if (args.length == 1 && args[0].equals(VERSION_OPTION)) {
            out.println("gabier " + ProtocolVersion.CURRENT);
            return OK;
        }
        err.println("usage: gabier " + VERSION_OPTION);
        return USAGE_ERROR;
    }
}
