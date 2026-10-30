package fr.craft.gabier.daemon;

import fr.craft.gabier.protocol.ProtocolVersion;
import fr.craft.gabier.shared.generation.ExcludeFromGeneratedCodeCoverage;
import java.io.PrintStream;

public final class Main {

    private static final String SELF_CHECK_OPTION = "--self-check";
    private static final int OK = 0;
    private static final int FAILED = 1;

    private Main() {}

    @ExcludeFromGeneratedCodeCoverage(
            reason = "The entry point ends in System.exit, which a unit test cannot cover: "
                    + "all its logic is in run, which is tested")
    public static void main(String[] args) {
        System.exit(run(args, SelfCheck.ofThisRuntime(), System.out, System.err));
    }

    static int run(String[] args, SelfCheck selfCheck, PrintStream out, PrintStream err) {
        if (args.length == 1 && args[0].equals(SELF_CHECK_OPTION)) {
            return runSelfCheck(selfCheck, out, err);
        }
        out.println("gabier-daemon " + ProtocolVersion.CURRENT);
        return OK;
    }

    private static int runSelfCheck(SelfCheck selfCheck, PrintStream out, PrintStream err) {
        var problems = selfCheck.problems();
        if (problems.isEmpty()) {
            out.println("self-check ok: " + selfCheck.summary());
            return OK;
        }
        err.println("self-check failed: " + String.join("; ", problems));
        return FAILED;
    }
}
